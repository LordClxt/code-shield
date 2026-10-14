// Package test : tests d'intégration (PostgreSQL réel) — parité avec la suite
// pytest v1 + nouveaux comportements (issues, MCP, synchro v2).
//
// Base : TEST_DATABASE_URL si défini, sinon un PostgreSQL embarqué
// (fergusstrange/embedded-postgres) démarré une fois pour le package.
package test

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
	"time"

	embeddedpostgres "github.com/fergusstrange/embedded-postgres"
	"github.com/jackc/pgx/v5/pgxpool"

	"codeshield/backend/internal/api"
	"codeshield/backend/internal/config"
	"codeshield/backend/internal/db"
)

var (
	pool *pgxpool.Pool
	cfg  config.Config
)

func freePort() uint32 {
	l, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		panic(err)
	}
	defer l.Close()
	return uint32(l.Addr().(*net.TCPAddr).Port)
}

func TestMain(m *testing.M) {
	if os.Getenv("TEST_VERBOSE_LOGS") == "" {
		slog.SetDefault(slog.New(slog.NewTextHandler(io.Discard, nil)))
	}
	os.Exit(run(m))
}

func run(m *testing.M) int {
	ctx := context.Background()
	url := os.Getenv("TEST_DATABASE_URL")
	if url == "" {
		port := freePort()
		tmp, err := os.MkdirTemp("", "codeshield-pg-*")
		if err != nil {
			panic(err)
		}
		defer os.RemoveAll(tmp)
		cacheDir, _ := os.UserCacheDir()
		pg := embeddedpostgres.NewDatabase(embeddedpostgres.DefaultConfig().
			Port(port).Database("codeshield").
			RuntimePath(filepath.Join(tmp, "runtime")).
			DataPath(filepath.Join(tmp, "data")).
			CachePath(filepath.Join(cacheDir, "codeshield-embedded-pg")).
			StartTimeout(90 * time.Second).
			Logger(io.Discard))
		if err := pg.Start(); err != nil {
			fmt.Fprintln(os.Stderr, "embedded postgres:", err)
			return 1
		}
		defer func() { _ = pg.Stop() }()
		url = fmt.Sprintf("postgres://postgres:postgres@127.0.0.1:%d/codeshield?sslmode=disable", port)
	}

	var err error
	if pool, err = db.Open(ctx, url); err != nil {
		fmt.Fprintln(os.Stderr, "db open:", err)
		return 1
	}
	defer pool.Close()
	if err := db.Migrate(ctx, pool); err != nil {
		fmt.Fprintln(os.Stderr, "migrate:", err)
		return 1
	}
	os.Setenv("ENVIRONMENT", "test")
	if cfg, err = config.Load(); err != nil {
		fmt.Fprintln(os.Stderr, "config:", err)
		return 1
	}
	return m.Run()
}

// reset vide toutes les tables entre deux tests.
func reset(t *testing.T) {
	t.Helper()
	_, err := pool.Exec(context.Background(),
		`TRUNCATE vulnerabilities, scans, api_keys, projects, users RESTART IDENTITY CASCADE`)
	if err != nil {
		t.Fatal(err)
	}
}

// ---------------------------------------------------------------- client HTTP

type client struct {
	t   *testing.T
	srv *httptest.Server
}

func newClient(t *testing.T) *client {
	t.Helper()
	reset(t)
	h, err := api.NewRouter(cfg, pool, api.Options{RateLimit: false})
	if err != nil {
		t.Fatal(err)
	}
	srv := httptest.NewServer(h)
	t.Cleanup(srv.Close)
	return &client{t: t, srv: srv}
}

type resp struct {
	Status int
	Body   []byte
}

func (r resp) JSON(t *testing.T, v any) {
	t.Helper()
	if err := json.Unmarshal(r.Body, v); err != nil {
		t.Fatalf("decode %s: %v", r.Body, err)
	}
}

func (r resp) Map(t *testing.T) map[string]any {
	var m map[string]any
	r.JSON(t, &m)
	return m
}

func (c *client) do(method, path, token string, body any) resp {
	c.t.Helper()
	var rd io.Reader
	if body != nil {
		b, err := json.Marshal(body)
		if err != nil {
			c.t.Fatal(err)
		}
		rd = bytes.NewReader(b)
	}
	req, err := http.NewRequest(method, c.srv.URL+path, rd)
	if err != nil {
		c.t.Fatal(err)
	}
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	res, err := http.DefaultClient.Do(req)
	if err != nil {
		c.t.Fatal(err)
	}
	defer res.Body.Close()
	b, _ := io.ReadAll(res.Body)
	return resp{Status: res.StatusCode, Body: b}
}

func (c *client) get(path, tok string) resp          { return c.do("GET", path, tok, nil) }
func (c *client) post(path, tok string, b any) resp  { return c.do("POST", path, tok, b) }
func (c *client) patch(path, tok string, b any) resp { return c.do("PATCH", path, tok, b) }
func (c *client) delete(path, tok string) resp       { return c.do("DELETE", path, tok, nil) }

// login enregistre (si besoin) et connecte un utilisateur ; retourne le JWT.
func (c *client) login(email string) string {
	c.t.Helper()
	creds := map[string]string{"email": email, "password": "supersecret1"}
	c.post("/api/v1/auth/register", "", creds)
	r := c.post("/api/v1/auth/login", "", creds)
	if r.Status != 200 {
		c.t.Fatalf("login: %d %s", r.Status, r.Body)
	}
	return r.Map(c.t)["access_token"].(string)
}

func (c *client) project(tok, name string) int64 {
	c.t.Helper()
	r := c.post("/api/v1/projects", tok, map[string]any{"name": name})
	if r.Status != 201 {
		c.t.Fatalf("create project: %d %s", r.Status, r.Body)
	}
	return int64(r.Map(c.t)["id"].(float64))
}

func (c *client) key(tok string, scopes []string, projectID *int64) string {
	c.t.Helper()
	body := map[string]any{"name": "agent", "scopes": scopes}
	if projectID != nil {
		body["project_id"] = *projectID
	}
	r := c.post("/api/v1/keys", tok, body)
	if r.Status != 201 {
		c.t.Fatalf("create key: %d %s", r.Status, r.Body)
	}
	return r.Map(c.t)["plain_key"].(string)
}

func finding(rule, path string, line int, sev string, extra map[string]any) map[string]any {
	f := map[string]any{
		"tool": "SEMGREP", "rule_id": rule, "file_path": path, "line_number": line,
		"severity": sev, "title": rule + " title",
	}
	for k, v := range extra {
		f[k] = v
	}
	return f
}

func (c *client) sync(tok string, pid int64, findings ...map[string]any) map[string]any {
	c.t.Helper()
	if findings == nil {
		findings = []map[string]any{}
	}
	r := c.post("/api/v1/sync/submit", tok, map[string]any{"project_id": pid, "findings": findings})
	if r.Status != 200 {
		c.t.Fatalf("sync: %d %s", r.Status, r.Body)
	}
	return r.Map(c.t)
}

func num(m map[string]any, k string) int { return int(m[k].(float64)) }
