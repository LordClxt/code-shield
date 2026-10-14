package v1

import (
	"context"
	"net/http"
	"strings"

	"codeshield/backend/internal/domain"
	"codeshield/backend/internal/service"
)

type ctxKey struct{}

func principalFrom(ctx context.Context) service.Principal {
	p, _ := ctx.Value(ctxKey{}).(service.Principal)
	return p
}

func WithPrincipal(ctx context.Context, p service.Principal) context.Context {
	return context.WithValue(ctx, ctxKey{}, p)
}

func BearerToken(r *http.Request) string {
	h := r.Header.Get("Authorization")
	scheme, tok, ok := strings.Cut(h, " ")
	if !ok || !strings.EqualFold(scheme, "bearer") {
		return ""
	}
	return strings.TrimSpace(tok)
}

// requireUser : session JWT uniquement (gestion de compte, projets, clés).
func (a *API) requireUser(next http.Handler) http.Handler {
	return a.authenticate(false, nil)(next)
}

// requireScope : JWT, ou clé API portant le scope demandé.
func (a *API) requireScope(scope domain.Scope) func(http.Handler) http.Handler {
	return a.authenticate(true, &scope)
}

func (a *API) authenticate(allowKey bool, scope *domain.Scope) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			tok := BearerToken(r)
			if tok == "" {
				writeError(w, http.StatusUnauthorized, "Missing bearer token")
				return
			}
			p, err := a.Auth.Authenticate(r.Context(), tok, allowKey)
			if err != nil {
				if !allowKey && strings.HasPrefix(tok, service.APIKeyPrefix) {
					writeError(w, http.StatusUnauthorized, "API keys are not accepted on this route")
					return
				}
				writeError(w, http.StatusUnauthorized, "Invalid or expired token")
				return
			}
			if scope != nil && !p.Has(*scope) {
				writeError(w, http.StatusForbidden, "API key lacks scope "+string(*scope))
				return
			}
			next.ServeHTTP(w, r.WithContext(WithPrincipal(r.Context(), p)))
		})
	}
}
