// Package api assemble le routeur HTTP racine (santé, OpenAPI, /api/v1, /mcp).
package api

import (
	_ "embed"
	"encoding/json"
	"log/slog"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/go-chi/cors"
	"github.com/go-chi/httprate"
	"github.com/jackc/pgx/v5/pgxpool"
	"gopkg.in/yaml.v3"

	"codeshield/backend/internal/api/v1"
	"codeshield/backend/internal/config"
	"codeshield/backend/internal/mcp"
)

// Copie de contracts/openapi.yaml (source de vérité, vérifiée par test).
//
//go:embed openapi.yaml
var openapiYAML []byte

type Options struct {
	RateLimit bool
}

func NewRouter(cfg config.Config, pool *pgxpool.Pool, opts Options) (http.Handler, error) {
	var spec any
	if err := yaml.Unmarshal(openapiYAML, &spec); err != nil {
		return nil, err
	}
	specJSON, err := json.Marshal(spec)
	if err != nil {
		return nil, err
	}

	a := v1.NewAPI(cfg, pool)
	mcpServer := &mcp.Server{Q: a.Q, Auth: a.Auth, Version: cfg.Version}

	r := chi.NewRouter()
	r.Use(middleware.RequestID, middleware.RealIP, requestLogger, middleware.Recoverer, securityHeaders)
	r.Use(cors.Handler(cors.Options{
		AllowedOrigins:   cfg.CORSOrigins,
		AllowedMethods:   []string{"GET", "POST", "PATCH", "DELETE", "OPTIONS"},
		AllowedHeaders:   []string{"Authorization", "Content-Type", "Mcp-Session-Id", "Mcp-Protocol-Version"},
		ExposedHeaders:   []string{"Mcp-Session-Id"},
		AllowCredentials: true,
		MaxAge:           600,
	}))

	r.Get("/health", func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]string{"status": "ok", "version": cfg.Version})
	})
	r.Get("/openapi.json", func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write(specJSON)
	})
	r.Get("/openapi.yaml", func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/yaml")
		_, _ = w.Write(openapiYAML)
	})

	r.Mount("/api/v1", a.Routes(opts.RateLimit))

	mcpHandler := mcpServer.Handler()
	if opts.RateLimit {
		mcpHandler = httprate.LimitByIP(600, time.Minute)(mcpHandler)
	}
	r.Handle("/mcp", mcpHandler)
	return r, nil
}

func securityHeaders(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		h := w.Header()
		h.Set("X-Content-Type-Options", "nosniff")
		h.Set("X-Frame-Options", "DENY")
		h.Set("Referrer-Policy", "no-referrer")
		h.Set("Cache-Control", "no-store")
		h.Set("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")
		next.ServeHTTP(w, r)
	})
}

func requestLogger(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		ww := middleware.NewWrapResponseWriter(w, r.ProtoMajor)
		next.ServeHTTP(ww, r)
		slog.InfoContext(r.Context(), "http",
			"method", r.Method, "path", r.URL.Path, "status", ww.Status(),
			"dur_ms", time.Since(start).Milliseconds(), "req_id", middleware.GetReqID(r.Context()))
	})
}
