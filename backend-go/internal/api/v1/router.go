package v1

import (
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/httprate"
	"github.com/jackc/pgx/v5/pgxpool"

	"codeshield/backend/internal/config"
	"codeshield/backend/internal/domain"
	"codeshield/backend/internal/repository"
	"codeshield/backend/internal/service"
)

type API struct {
	Cfg    config.Config
	Pool   *pgxpool.Pool
	Q      *repository.Queries
	Auth   service.Authenticator
	Tokens service.Tokens
	Sync   service.SyncService
}

func NewAPI(cfg config.Config, pool *pgxpool.Pool) *API {
	q := repository.New(pool)
	tokens := service.Tokens{Secret: cfg.JWTSecret, Expires: cfg.JWTExpires}
	return &API{
		Cfg: cfg, Pool: pool, Q: q, Tokens: tokens,
		Auth: service.Authenticator{Q: q, Tokens: tokens},
		Sync: service.SyncService{Pool: pool},
	}
}

// Routes monte /api/v1. rateLimit=false dans les tests.
func (a *API) Routes(rateLimit bool) http.Handler {
	limit := func(n int, per time.Duration) func(http.Handler) http.Handler {
		if !rateLimit {
			return func(h http.Handler) http.Handler { return h }
		}
		return httprate.LimitByIP(n, per)
	}

	r := chi.NewRouter()

	r.Route("/auth", func(r chi.Router) {
		r.With(limit(10, time.Minute)).Post("/register", a.register)
		r.With(limit(20, time.Minute)).Post("/login", a.login)
		r.With(a.requireUser).Get("/me", a.me)
	})

	r.Route("/projects", func(r chi.Router) {
		r.Use(a.requireUser)
		r.Post("/", a.createProject)
		r.Get("/", a.listProjects)
		r.Route("/{projectID}", func(r chi.Router) {
			r.Get("/", a.getProject)
			r.Delete("/", a.deleteProject)
			r.Get("/vulnerabilities", a.listProjectVulns)
			r.Get("/scans", a.listScans)
			r.Get("/metrics", a.projectMetrics)
			r.Get("/timeline", a.projectTimeline)
		})
	})

	r.With(limit(120, time.Minute), a.requireScope(domain.ScopeSyncWrite)).Post("/sync/submit", a.submitSync)

	r.Route("/keys", func(r chi.Router) {
		r.Use(a.requireUser)
		r.Post("/", a.createKey)
		r.Get("/", a.listKeys)
		r.Delete("/{keyID}", a.revokeKey)
	})

	r.Route("/issues", func(r chi.Router) {
		r.Use(limit(600, time.Minute))
		r.With(a.requireScope(domain.ScopeIssuesRead)).Get("/", a.listIssues)
		r.With(a.requireScope(domain.ScopeIssuesRead)).Get("/{issueID}", a.getIssue)
		r.With(a.requireScope(domain.ScopeIssuesWrite)).Patch("/{issueID}", a.patchIssue)
		r.With(a.requireScope(domain.ScopeIssuesWrite)).Post("/bulk", a.bulkIssues)
	})
	return r
}
