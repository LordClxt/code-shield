package v1

import (
	"errors"
	"net/http"
	"slices"
	"strings"
	"unicode/utf8"

	"codeshield/backend/internal/domain"
	"codeshield/backend/internal/repository"
	"codeshield/backend/internal/service"
)

type keyCreate struct {
	Name      string         `json:"name"`
	Scopes    []domain.Scope `json:"scopes"`
	ProjectID *int64         `json:"project_id"`
}

type keyCreated struct {
	domain.APIKey
	PlainKey string `json:"plain_key"`
}

func (a *API) createKey(w http.ResponseWriter, r *http.Request) {
	var body keyCreate
	if !decodeJSON(w, r, &body, false) {
		return
	}
	var errs []domain.FieldError
	if n := utf8.RuneCountInString(strings.TrimSpace(body.Name)); n < 1 || n > 200 {
		errs = append(errs, domain.FieldError{Loc: []any{"body", "name"}, Msg: "length must be between 1 and 200"})
	}
	if len(body.Scopes) == 0 {
		// Compat v1 : une clé sans scope explicite sert à la synchro desktop.
		body.Scopes = []domain.Scope{domain.ScopeSyncWrite}
	}
	for i, s := range body.Scopes {
		if !s.Valid() {
			errs = append(errs, domain.FieldError{Loc: []any{"body", "scopes", i}, Msg: "invalid scope"})
		}
	}
	slices.Sort(body.Scopes)
	body.Scopes = slices.Compact(body.Scopes)
	if len(errs) > 0 {
		writeValidation(w, errs)
		return
	}
	user := principalFrom(r.Context()).User
	if body.ProjectID != nil {
		if _, err := a.Q.GetOwnedProject(r.Context(), *body.ProjectID, user.ID); errors.Is(err, repository.ErrNotFound) {
			writeError(w, http.StatusNotFound, "Project not found")
			return
		} else if err != nil {
			internalError(w, r, err)
			return
		}
	}
	plain, hash, prefix, err := service.GenerateAPIKey()
	if err != nil {
		internalError(w, r, err)
		return
	}
	k, err := a.Q.CreateAPIKey(r.Context(), user.ID, body.Name, hash, prefix, body.Scopes, body.ProjectID)
	if err != nil {
		internalError(w, r, err)
		return
	}
	writeJSON(w, http.StatusCreated, keyCreated{APIKey: k, PlainKey: plain})
}

func (a *API) listKeys(w http.ResponseWriter, r *http.Request) {
	ks, err := a.Q.ListAPIKeys(r.Context(), principalFrom(r.Context()).User.ID)
	if err != nil {
		internalError(w, r, err)
		return
	}
	writeJSON(w, http.StatusOK, nonNilSlice(ks))
}

func (a *API) revokeKey(w http.ResponseWriter, r *http.Request) {
	id, ok := pathID(w, r, "keyID")
	if !ok {
		return
	}
	err := a.Q.RevokeAPIKey(r.Context(), id, principalFrom(r.Context()).User.ID)
	if errors.Is(err, repository.ErrNotFound) {
		writeError(w, http.StatusNotFound, "API key not found")
		return
	}
	if err != nil {
		internalError(w, r, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}
