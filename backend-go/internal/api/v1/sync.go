package v1

import (
	"errors"
	"net/http"

	"codeshield/backend/internal/domain"
	"codeshield/backend/internal/repository"
)

func (a *API) submitSync(w http.ResponseWriter, r *http.Request) {
	var body domain.SyncSubmitRequest
	// strict : tout champ hors contrat (snippet, source_code…) -> 422.
	if !decodeJSON(w, r, &body, true) {
		return
	}
	if errs := body.Validate(); len(errs) > 0 {
		writeValidation(w, errs)
		return
	}
	if len(body.Findings) > a.Cfg.MaxFindingsPerSync {
		writeError(w, http.StatusRequestEntityTooLarge, "Too many findings")
		return
	}
	p := principalFrom(r.Context())
	if !p.CanAccessProject(body.ProjectID) {
		writeError(w, http.StatusNotFound, "Project not found")
		return
	}
	if _, err := a.Q.GetOwnedProject(r.Context(), body.ProjectID, p.User.ID); errors.Is(err, repository.ErrNotFound) {
		writeError(w, http.StatusNotFound, "Project not found")
		return
	} else if err != nil {
		internalError(w, r, err)
		return
	}
	res, err := a.Sync.Submit(r.Context(), body)
	if err != nil {
		internalError(w, r, err)
		return
	}
	writeJSON(w, http.StatusOK, res)
}
