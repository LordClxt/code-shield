// Package v1 expose les routes HTTP /api/v1 (contrôleurs).
package v1

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"strconv"
	"strings"

	"github.com/go-chi/chi/v5"

	"codeshield/backend/internal/domain"
)

const maxBodyBytes = 16 << 20

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

// Format d'erreur compatible FastAPI : {"detail": "..."}.
func writeError(w http.ResponseWriter, status int, detail string) {
	writeJSON(w, status, map[string]any{"detail": detail})
}

func writeValidation(w http.ResponseWriter, errs []domain.FieldError) {
	writeJSON(w, http.StatusUnprocessableEntity, map[string]any{"detail": errs})
}

func internalError(w http.ResponseWriter, r *http.Request, err error) {
	slog.ErrorContext(r.Context(), "internal error", "err", err, "path", r.URL.Path)
	writeError(w, http.StatusInternalServerError, "Internal server error")
}

// decodeJSON lit un corps JSON unique. strict=true rejette tout champ inconnu
// (garde-fou anti code source sur /sync/submit).
func decodeJSON(w http.ResponseWriter, r *http.Request, dst any, strict bool) bool {
	dec := json.NewDecoder(http.MaxBytesReader(w, r.Body, maxBodyBytes))
	if strict {
		dec.DisallowUnknownFields()
	}
	if err := dec.Decode(dst); err != nil {
		var tooBig *http.MaxBytesError
		if errors.As(err, &tooBig) {
			writeError(w, http.StatusRequestEntityTooLarge, "Request body too large")
			return false
		}
		msg := err.Error()
		if strings.HasPrefix(msg, "json: unknown field") {
			msg = "extra field not permitted: " + strings.TrimPrefix(msg, "json: unknown field ")
		}
		writeValidation(w, []domain.FieldError{{Loc: []any{"body"}, Msg: msg}})
		return false
	}
	if _, err := dec.Token(); !errors.Is(err, io.EOF) {
		writeValidation(w, []domain.FieldError{{Loc: []any{"body"}, Msg: "unexpected data after JSON body"}})
		return false
	}
	return true
}

func pathID(w http.ResponseWriter, r *http.Request, name string) (int64, bool) {
	id, err := strconv.ParseInt(chi.URLParam(r, name), 10, 64)
	if err != nil || id <= 0 {
		writeValidation(w, []domain.FieldError{{Loc: []any{"path", name}, Msg: "must be a positive integer"}})
		return 0, false
	}
	return id, true
}

// parseEnumList lit un paramètre de requête (CSV ou répété) validé par valid.
func parseEnumList[T ~string](r *http.Request, name string, valid func(T) bool) ([]T, *domain.FieldError) {
	var out []T
	for _, raw := range r.URL.Query()[name] {
		for _, part := range strings.Split(raw, ",") {
			part = strings.TrimSpace(part)
			if part == "" {
				continue
			}
			v := T(strings.ToUpper(part))
			if !valid(v) {
				return nil, &domain.FieldError{Loc: []any{"query", name}, Msg: fmt.Sprintf("invalid value %q", part)}
			}
			out = append(out, v)
		}
	}
	return out, nil
}
