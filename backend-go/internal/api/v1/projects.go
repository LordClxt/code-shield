package v1

import (
	"errors"
	"net/http"
	"strconv"
	"strings"
	"unicode/utf8"

	"codeshield/backend/internal/domain"
	"codeshield/backend/internal/repository"
	"codeshield/backend/internal/service"
)

type projectCreate struct {
	Name    string  `json:"name"`
	RepoURL *string `json:"repo_url"`
}

func (a *API) createProject(w http.ResponseWriter, r *http.Request) {
	var body projectCreate
	if !decodeJSON(w, r, &body, false) {
		return
	}
	var errs []domain.FieldError
	if n := utf8.RuneCountInString(strings.TrimSpace(body.Name)); n < 1 || n > 200 {
		errs = append(errs, domain.FieldError{Loc: []any{"body", "name"}, Msg: "length must be between 1 and 200"})
	}
	if body.RepoURL != nil && utf8.RuneCountInString(*body.RepoURL) > 500 {
		errs = append(errs, domain.FieldError{Loc: []any{"body", "repo_url"}, Msg: "at most 500 characters"})
	}
	if len(errs) > 0 {
		writeValidation(w, errs)
		return
	}
	p, err := a.Q.CreateProject(r.Context(), principalFrom(r.Context()).User.ID, body.Name, body.RepoURL)
	if err != nil {
		internalError(w, r, err)
		return
	}
	writeJSON(w, http.StatusCreated, p)
}

func (a *API) listProjects(w http.ResponseWriter, r *http.Request) {
	ps, err := a.Q.ListProjects(r.Context(), principalFrom(r.Context()).User.ID)
	if err != nil {
		internalError(w, r, err)
		return
	}
	writeJSON(w, http.StatusOK, nonNilSlice(ps))
}

// ownedProject résout {projectID} et vérifie la propriété (404 sinon).
func (a *API) ownedProject(w http.ResponseWriter, r *http.Request) (domain.Project, bool) {
	id, ok := pathID(w, r, "projectID")
	if !ok {
		return domain.Project{}, false
	}
	p, err := a.Q.GetOwnedProject(r.Context(), id, principalFrom(r.Context()).User.ID)
	if errors.Is(err, repository.ErrNotFound) {
		writeError(w, http.StatusNotFound, "Project not found")
		return p, false
	}
	if err != nil {
		internalError(w, r, err)
		return p, false
	}
	return p, true
}

func (a *API) getProject(w http.ResponseWriter, r *http.Request) {
	if p, ok := a.ownedProject(w, r); ok {
		writeJSON(w, http.StatusOK, p)
	}
}

func (a *API) deleteProject(w http.ResponseWriter, r *http.Request) {
	p, ok := a.ownedProject(w, r)
	if !ok {
		return
	}
	if err := a.Q.DeleteProject(r.Context(), p.ID); err != nil {
		internalError(w, r, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (a *API) listProjectVulns(w http.ResponseWriter, r *http.Request) {
	p, ok := a.ownedProject(w, r)
	if !ok {
		return
	}
	var f repository.VulnFilter
	if s := r.URL.Query().Get("status"); s != "" {
		st := domain.Status(s)
		if !st.Valid() {
			writeValidation(w, []domain.FieldError{{Loc: []any{"query", "status"}, Msg: "invalid status"}})
			return
		}
		f.Status = &st
	}
	if s := r.URL.Query().Get("severity"); s != "" {
		sev := domain.Severity(s)
		if !sev.Valid() {
			writeValidation(w, []domain.FieldError{{Loc: []any{"query", "severity"}, Msg: "invalid severity"}})
			return
		}
		f.Severity = &sev
	}
	vs, err := a.Q.ListProjectVulns(r.Context(), p.ID, f)
	if err != nil {
		internalError(w, r, err)
		return
	}
	writeJSON(w, http.StatusOK, nonNilSlice(vs))
}

func (a *API) listScans(w http.ResponseWriter, r *http.Request) {
	p, ok := a.ownedProject(w, r)
	if !ok {
		return
	}
	ss, err := a.Q.ListScans(r.Context(), p.ID, 50)
	if err != nil {
		internalError(w, r, err)
		return
	}
	writeJSON(w, http.StatusOK, nonNilSlice(ss))
}

type projectMetrics struct {
	ProjectID     int64                   `json:"project_id"`
	Score         int                     `json:"score"`
	Grade         string                  `json:"grade"`
	OpenCounts    map[domain.Severity]int `json:"open_counts"`
	ResolvedCount int                     `json:"resolved_count"`
	IgnoredCount  int                     `json:"ignored_count"`
	TotalScans    int                     `json:"total_scans"`
}

func (a *API) projectMetrics(w http.ResponseWriter, r *http.Request) {
	p, ok := a.ownedProject(w, r)
	if !ok {
		return
	}
	ctx := r.Context()
	counts, err := a.Q.CountActiveBySeverity(ctx, p.ID)
	if err != nil {
		internalError(w, r, err)
		return
	}
	m := projectMetrics{ProjectID: p.ID, OpenCounts: counts}
	m.Score, m.Grade = service.ComputeScore(counts)
	if m.ResolvedCount, err = a.Q.CountByStatus(ctx, p.ID, domain.StatusResolved); err != nil {
		internalError(w, r, err)
		return
	}
	if m.IgnoredCount, err = a.Q.CountByStatus(ctx, p.ID, domain.StatusIgnored); err != nil {
		internalError(w, r, err)
		return
	}
	if m.TotalScans, err = a.Q.CountScans(ctx, p.ID); err != nil {
		internalError(w, r, err)
		return
	}
	writeJSON(w, http.StatusOK, m)
}

func (a *API) projectTimeline(w http.ResponseWriter, r *http.Request) {
	p, ok := a.ownedProject(w, r)
	if !ok {
		return
	}
	days := 30
	if s := r.URL.Query().Get("days"); s != "" {
		n, err := strconv.Atoi(s)
		if err != nil || n < 1 || n > 365 {
			writeValidation(w, []domain.FieldError{{Loc: []any{"query", "days"}, Msg: "must be between 1 and 365"}})
			return
		}
		days = n
	}
	pts, err := a.Q.Timeline(r.Context(), p.ID, days)
	if err != nil {
		internalError(w, r, err)
		return
	}
	writeJSON(w, http.StatusOK, pts)
}

func nonNilSlice[T any](xs []T) []T {
	if xs == nil {
		return []T{}
	}
	return xs
}
