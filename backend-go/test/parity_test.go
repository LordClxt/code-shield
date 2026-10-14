package test

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"strings"
	"testing"
)

// Parité avec backend/tests/*.py (FastAPI v1).

func TestAuthRegisterLoginMe(t *testing.T) {
	c := newClient(t)
	creds := map[string]string{"email": "Bob@Example.com", "password": "supersecret1"}
	r := c.post("/api/v1/auth/register", "", creds)
	if r.Status != 201 || r.Map(t)["email"] != "bob@example.com" {
		t.Fatalf("register: %d %s", r.Status, r.Body)
	}
	if r := c.post("/api/v1/auth/register", "", creds); r.Status != 400 {
		t.Fatalf("duplicate register: %d", r.Status)
	}
	if r := c.post("/api/v1/auth/login", "", map[string]string{"email": "bob@example.com", "password": "wrongpass"}); r.Status != 401 {
		t.Fatalf("bad login: %d", r.Status)
	}
	if r := c.post("/api/v1/auth/register", "", map[string]string{"email": "nope", "password": "supersecret1"}); r.Status != 422 {
		t.Fatalf("invalid email: %d", r.Status)
	}
	if r := c.post("/api/v1/auth/register", "", map[string]string{"email": "c@example.com", "password": "short"}); r.Status != 422 {
		t.Fatalf("short password: %d", r.Status)
	}
	tok := c.login("bob@example.com")
	me := c.get("/api/v1/auth/me", tok)
	if me.Status != 200 || me.Map(t)["email"] != "bob@example.com" {
		t.Fatalf("me: %d %s", me.Status, me.Body)
	}
}

func TestProjectsAreIsolatedPerUser(t *testing.T) {
	c := newClient(t)
	alice, bob := c.login("alice@example.com"), c.login("bob@example.com")
	pid := c.project(alice, "web")
	var list []map[string]any
	c.get("/api/v1/projects", bob).JSON(t, &list)
	if len(list) != 0 {
		t.Fatalf("bob sees %v", list)
	}
	if r := c.get(fmt.Sprintf("/api/v1/projects/%d", pid), bob); r.Status != 404 {
		t.Fatalf("bob get: %d", r.Status)
	}
	if r := c.get(fmt.Sprintf("/api/v1/projects/%d", pid), alice); r.Status != 200 {
		t.Fatalf("alice get: %d", r.Status)
	}
	if r := c.delete(fmt.Sprintf("/api/v1/projects/%d", pid), alice); r.Status != 204 {
		t.Fatalf("delete: %d", r.Status)
	}
}

func TestUnauthenticatedRejected(t *testing.T) {
	c := newClient(t)
	for _, p := range []string{"/api/v1/projects", "/api/v1/auth/me", "/api/v1/keys", "/api/v1/issues"} {
		if r := c.get(p, ""); r.Status != 401 {
			t.Errorf("%s: %d", p, r.Status)
		}
	}
	if r := c.get("/api/v1/projects", "garbage"); r.Status != 401 {
		t.Errorf("garbage token: %d", r.Status)
	}
}

func TestSyncSubmitEndToEnd(t *testing.T) {
	c := newClient(t)
	tok := c.login("alice@example.com")
	pid := c.project(tok, "web")

	res := c.sync(tok, pid, finding("python.lang.security.audit.dangerous-exec", "src/app.py", 42, "HIGH", nil))
	if num(res, "created") != 1 || num(res, "open_total") != 1 || num(res, "score") != 95 || res["grade"] != "A" {
		t.Fatalf("first sync: %v", res)
	}
	var vulns []map[string]any
	c.get(fmt.Sprintf("/api/v1/projects/%d/vulnerabilities", pid), tok).JSON(t, &vulns)
	if len(vulns) != 1 || !strings.HasSuffix(vulns[0]["rule_id"].(string), "dangerous-exec") || vulns[0]["category"] != "SAST" {
		t.Fatalf("vulns: %v", vulns)
	}
	m := c.get(fmt.Sprintf("/api/v1/projects/%d/metrics", pid), tok).Map(t)
	if num(m["open_counts"].(map[string]any), "HIGH") != 1 || num(m, "total_scans") != 1 {
		t.Fatalf("metrics: %v", m)
	}

	res2 := c.sync(tok, pid)
	if num(res2, "resolved") != 1 {
		t.Fatalf("second sync: %v", res2)
	}
	m2 := c.get(fmt.Sprintf("/api/v1/projects/%d/metrics", pid), tok).Map(t)
	if num(m2, "resolved_count") != 1 || num(m2, "score") != 100 {
		t.Fatalf("metrics2: %v", m2)
	}
	var tl []map[string]any
	c.get(fmt.Sprintf("/api/v1/projects/%d/timeline?days=7", pid), tok).JSON(t, &tl)
	if len(tl) != 7 || num(tl[6], "opened") != 1 || num(tl[6], "resolved") != 1 {
		t.Fatalf("timeline: %v", tl)
	}
}

func TestSyncRequiresAuthAndOwnership(t *testing.T) {
	c := newClient(t)
	owner, other := c.login("owner@example.com"), c.login("intruder@example.com")
	pid := c.project(owner, "p")
	body := map[string]any{"project_id": pid, "findings": []any{finding("R", "a.py", 1, "LOW", nil)}}
	if r := c.post("/api/v1/sync/submit", "", body); r.Status != 401 {
		t.Fatalf("anon: %d", r.Status)
	}
	if r := c.post("/api/v1/sync/submit", other, body); r.Status != 404 {
		t.Fatalf("other: %d", r.Status)
	}
}

func TestAPIKeyCanSubmitSyncAndRevoke(t *testing.T) {
	c := newClient(t)
	tok := c.login("desktop@example.com")
	pid := c.project(tok, "p")
	r := c.post("/api/v1/keys", tok, map[string]any{"name": "laptop"})
	if r.Status != 201 {
		t.Fatalf("create key: %d %s", r.Status, r.Body)
	}
	k := r.Map(t)
	plain := k["plain_key"].(string)
	if !strings.HasPrefix(plain, "csk_") || fmt.Sprint(k["scopes"]) != "[sync:write]" {
		t.Fatalf("key: %v", k)
	}
	var keys []map[string]any
	c.get("/api/v1/keys", tok).JSON(t, &keys)
	if _, leaked := keys[0]["plain_key"]; leaked {
		t.Fatal("plain_key re-exposed")
	}
	c.sync(plain, pid, finding("R", "a.py", 1, "LOW", nil))

	c.get("/api/v1/keys", tok).JSON(t, &keys)
	if keys[0]["last_used_at"] == nil {
		t.Fatal("last_used_at not updated")
	}
	// Une clé API ne gère pas le compte.
	if r := c.get("/api/v1/projects", plain); r.Status != 401 {
		t.Fatalf("key on /projects: %d", r.Status)
	}
	if r := c.delete(fmt.Sprintf("/api/v1/keys/%d", int64(k["id"].(float64))), tok); r.Status != 204 {
		t.Fatalf("revoke: %d", r.Status)
	}
	body := map[string]any{"project_id": pid, "findings": []any{}}
	if r := c.post("/api/v1/sync/submit", plain, body); r.Status != 401 {
		t.Fatalf("revoked key: %d", r.Status)
	}
}

// ------------------------------------------------- garde-fou anti code source

func TestSyncRejectsSourceCodeFields(t *testing.T) {
	c := newClient(t)
	tok := c.login("leak@example.com")
	pid := c.project(tok, "p")
	for _, field := range []string{"source_code", "snippet", "code", "source", "raw_code"} {
		f := finding("R", "a.py", 1, "LOW", map[string]any{field: "print('secret')"})
		if r := c.post("/api/v1/sync/submit", tok, map[string]any{"project_id": pid, "findings": []any{f}}); r.Status != 422 {
			t.Errorf("finding.%s: %d", field, r.Status)
		}
		if r := c.post("/api/v1/sync/submit", tok, map[string]any{"project_id": pid, "findings": []any{}, field: "x"}); r.Status != 422 {
			t.Errorf("request.%s: %d", field, r.Status)
		}
	}
}

func TestNoTableHasSourceColumn(t *testing.T) {
	rows, err := pool.Query(context.Background(), `SELECT table_name, column_name FROM information_schema.columns
		WHERE table_schema = 'public' AND column_name = ANY($1)`,
		[]string{"source_code", "snippet", "code", "source", "raw_code", "line_text", "content"})
	if err != nil {
		t.Fatal(err)
	}
	defer rows.Close()
	for rows.Next() {
		var tbl, col string
		_ = rows.Scan(&tbl, &col)
		t.Errorf("forbidden column %s.%s", tbl, col)
	}
}

func TestSyncValidation(t *testing.T) {
	c := newClient(t)
	tok := c.login("v@example.com")
	pid := c.project(tok, "p")
	bad := []map[string]any{
		finding("R", "a.py", 0, "LOW", nil),
		finding("R", "a.py", 1, "SEVERE", nil),
		finding("R", "a.py", 1, "LOW", map[string]any{"tool": "NMAP"}),
		finding("R", "a.py", 5, "LOW", map[string]any{"end_line": 2}),
		finding("R", "a.py", 1, "LOW", map[string]any{"fingerprint": "not-a-hash"}),
		finding("R", "a.py", 1, "LOW", map[string]any{"cwe": []string{"89"}}),
		finding("", "a.py", 1, "LOW", nil),
	}
	for i, f := range bad {
		if r := c.post("/api/v1/sync/submit", tok, map[string]any{"project_id": pid, "findings": []any{f}}); r.Status != 422 {
			t.Errorf("case %d: %d %s", i, r.Status, r.Body)
		}
	}
	if r := c.post("/api/v1/sync/submit", tok, map[string]any{"findings": []any{}}); r.Status != 422 {
		t.Errorf("missing project_id: %d", r.Status)
	}
}

// La fixture partagée (aussi testée côté Kotlin et TS) est acceptée telle quelle.
func TestSharedSyncFixtureIsAccepted(t *testing.T) {
	raw, err := os.ReadFile("../../contracts/fixtures/sync_request.json")
	if err != nil {
		t.Skip("contracts/fixtures introuvable")
	}
	c := newClient(t)
	tok := c.login("fixture@example.com")
	pid := c.project(tok, "fixture")
	var body map[string]any
	if err := json.Unmarshal(raw, &body); err != nil {
		t.Fatal(err)
	}
	body["project_id"] = pid
	r := c.post("/api/v1/sync/submit", tok, body)
	if r.Status != 200 {
		t.Fatalf("fixture rejected: %d %s", r.Status, r.Body)
	}
	res := r.Map(t)
	if num(res, "created") != 3 || num(res, "open_total") != 3 {
		t.Fatalf("%v", res)
	}
	var vulns []map[string]any
	c.get(fmt.Sprintf("/api/v1/projects/%d/vulnerabilities", pid), tok).JSON(t, &vulns)
	for _, v := range vulns {
		if v["tool"] == "TRIVY" && (v["package"] != "flask" || v["fixed_version"] != "2.2.5" || v["category"] != "SCA") {
			t.Fatalf("sca fields lost: %v", v)
		}
	}
}

func TestOpenAPIContractInSync(t *testing.T) {
	a, err := os.ReadFile("../../contracts/openapi.yaml")
	if err != nil {
		t.Skip("contracts/openapi.yaml introuvable (build hors monorepo)")
	}
	b, err := os.ReadFile("../internal/api/openapi.yaml")
	if err != nil {
		t.Fatal(err)
	}
	if string(a) != string(b) {
		t.Fatal("internal/api/openapi.yaml diverge de contracts/openapi.yaml : lancez `make contracts`")
	}
	c := newClient(t)
	if r := c.get("/openapi.json", ""); r.Status != 200 || r.Map(t)["openapi"] != "3.1.0" {
		t.Fatalf("openapi.json: %d", r.Status)
	}
}
