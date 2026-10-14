package v1

import (
	"errors"
	"net/http"
	"strconv"

	"codeshield/backend/internal/domain"
	"codeshield/backend/internal/repository"
	"codeshield/backend/internal/service"
)

func (a *API) issues() service.IssueService { return service.IssueService{Q: a.Q} }

// GET /issues : liste transverse paginée, failles les plus graves d'abord.
func (a *API) listIssues(w http.ResponseWriter, r *http.Request) {
	qs := r.URL.Query()
	in := service.IssueQuery{Cursor: qs.Get("cursor"), FilePrefix: qs.Get("file_prefix")}
	var fe *domain.FieldError
	if in.Statuses, fe = parseEnumList(r, "status", domain.Status.Valid); fe != nil {
		writeValidation(w, []domain.FieldError{*fe})
		return
	}
	if in.Severities, fe = parseEnumList(r, "severity", domain.Severity.Valid); fe != nil {
		writeValidation(w, []domain.FieldError{*fe})
		return
	}
	if in.Tools, fe = parseEnumList(r, "tool", domain.Tool.Valid); fe != nil {
		writeValidation(w, []domain.FieldError{*fe})
		return
	}
	if in.Categories, fe = parseEnumList(r, "category", domain.Category.Valid); fe != nil {
		writeValidation(w, []domain.FieldError{*fe})
		return
	}
	if s := qs.Get("project_id"); s != "" {
		id, err := strconv.ParseInt(s, 10, 64)
		if err != nil || id <= 0 {
			writeValidation(w, []domain.FieldError{{Loc: []any{"query", "project_id"}, Msg: "must be a positive integer"}})
			return
		}
		in.ProjectID = &id
	}
	if s := qs.Get("limit"); s != "" {
		n, err := strconv.Atoi(s)
		if err != nil || n < 1 || n > 200 {
			writeValidation(w, []domain.FieldError{{Loc: []any{"query", "limit"}, Msg: "must be between 1 and 200"}})
			return
		}
		in.Limit = n
	}
	page, err := a.issues().List(r.Context(), principalFrom(r.Context()), in)
	if errors.Is(err, service.ErrBadCursor) {
		writeValidation(w, []domain.FieldError{{Loc: []any{"query", "cursor"}, Msg: "invalid cursor"}})
		return
	}
	if err != nil {
		internalError(w, r, err)
		return
	}
	writeJSON(w, http.StatusOK, page)
}

// GET /issues/{id} : faille + fix_context (guidance de correction pour agent).
func (a *API) getIssue(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r, "issueID")
	if !ok {
		return
	}
	d, err := a.issues().Get(r.Context(), principalFrom(r.Context()), id)
	if errors.Is(err, repository.ErrNotFound) {
		writeError(w, http.StatusNotFound, "Issue not found")
		return
	}
	if err != nil {
		internalError(w, r, err)
		return
	}
	writeJSON(w, http.StatusOK, d)
}

type statusUpdate struct {
	Status domain.Status `json:"status"`
	Note   *string       `json:"note"`
}

func (u statusUpdate) validate() []domain.FieldError {
	var errs []domain.FieldError
	if !u.Status.Valid() {
		errs = append(errs, domain.FieldError{Loc: []any{"body", "status"}, Msg: "invalid status"})
	}
	if u.Note != nil && len(*u.Note) > 2000 {
		errs = append(errs, domain.FieldError{Loc: []any{"body", "note"}, Msg: "at most 2000 characters"})
	}
	return errs
}

func (a *API) applyStatus(w http.ResponseWriter, r *http.Request, ids []int64, u statusUpdate) ([]int64, bool) {
	if errs := u.validate(); len(errs) > 0 {
		writeValidation(w, errs)
		return nil, false
	}
	updated, err := a.issues().SetStatus(r.Context(), principalFrom(r.Context()), ids, u.Status, u.Note)
	if errors.Is(err, service.ErrForbiddenTransition) {
		writeError(w, http.StatusConflict, err.Error())
		return nil, false
	}
	if err != nil {
		internalError(w, r, err)
		return nil, false
	}
	return updated, true
}

// PATCH /issues/{id} : {status, note}.
func (a *API) patchIssue(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r, "issueID")
	if !ok {
		return
	}
	var body statusUpdate
	if !decodeJSON(w, r, &body, true) {
		return
	}
	updated, ok := a.applyStatus(w, r, []int64{id}, body)
	if !ok {
		return
	}
	if len(updated) == 0 {
		writeError(w, http.StatusNotFound, "Issue not found")
		return
	}
	a.getIssue(w, r)
}

type bulkUpdate struct {
	IDs    []int64       `json:"ids"`
	Status domain.Status `json:"status"`
	Note   *string       `json:"note"`
}

// POST /issues/bulk : {ids, status, note} -> {updated: [ids]}.
func (a *API) bulkIssues(w http.ResponseWriter, r *http.Request) {
	var body bulkUpdate
	if !decodeJSON(w, r, &body, true) {
		return
	}
	if len(body.IDs) == 0 || len(body.IDs) > 500 {
		writeValidation(w, []domain.FieldError{{Loc: []any{"body", "ids"}, Msg: "between 1 and 500 ids"}})
		return
	}
	updated, ok := a.applyStatus(w, r, body.IDs, statusUpdate{Status: body.Status, Note: body.Note})
	if !ok {
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"updated": nonNilSlice(updated)})
}
