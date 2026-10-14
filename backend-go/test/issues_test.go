package test

import (
	"fmt"
	"strings"
	"testing"
)

func TestIssuesAPIScopesAndGuidance(t *testing.T) {
	c := newClient(t)
	tok := c.login("agent@example.com")
	pid := c.project(tok, "api")
	c.sync(tok, pid,
		finding("python.sqlalchemy.security.sqlalchemy-execute-raw-query", "app/db.py", 12, "CRITICAL",
			map[string]any{"cwe": []string{"CWE-89"}, "end_line": 14, "column": 5}),
		finding("generic-api-key", "config.py", 3, "HIGH", map[string]any{"tool": "GITLEAKS"}),
		finding("CVE-2024-0001", "requirements.txt", 1, "MEDIUM",
			map[string]any{"tool": "TRIVY", "category": "SCA", "package": "flask", "installed_version": "2.0.0", "fixed_version": "2.3.3"}),
	)

	syncOnly := c.key(tok, []string{"sync:write"}, nil)
	if r := c.get("/api/v1/issues", syncOnly); r.Status != 403 {
		t.Fatalf("sync-only key on issues: %d", r.Status)
	}
	reader := c.key(tok, []string{"issues:read"}, nil)

	var page struct {
		Items []map[string]any `json:"items"`
		Next  *string          `json:"next_cursor"`
	}
	c.get("/api/v1/issues", reader).JSON(t, &page)
	if len(page.Items) != 3 || page.Items[0]["severity"] != "CRITICAL" || page.Items[0]["project_name"] != "api" {
		t.Fatalf("list: %v", page.Items)
	}
	id := int64(page.Items[0]["id"].(float64))

	d := c.get(fmt.Sprintf("/api/v1/issues/%d", id), reader).Map(t)
	fc := d["fix_context"].(map[string]any)
	g := fc["guidance"].(map[string]any)
	if fc["file_path"] != "app/db.py" || num(fc, "line_start") != 12 || num(fc, "line_end") != 14 || g["category"] != "Injection SQL" {
		t.Fatalf("fix_context: %v", fc)
	}

	// Lecture seule : pas d'écriture.
	if r := c.patch(fmt.Sprintf("/api/v1/issues/%d", id), reader, map[string]any{"status": "IN_PROGRESS"}); r.Status != 403 {
		t.Fatalf("reader patch: %d", r.Status)
	}

	writer := c.key(tok, []string{"issues:read", "issues:write"}, nil)
	if r := c.patch(fmt.Sprintf("/api/v1/issues/%d", id), writer, map[string]any{"status": "RESOLVED"}); r.Status != 409 {
		t.Fatalf("agent RESOLVED should be refused: %d", r.Status)
	}
	if r := c.patch(fmt.Sprintf("/api/v1/issues/%d", id), writer, map[string]any{"status": "REOPENED"}); r.Status != 409 {
		t.Fatalf("REOPENED should be refused: %d", r.Status)
	}
	if r := c.patch(fmt.Sprintf("/api/v1/issues/%d", id), writer, map[string]any{"status": "IGNORED", "note": "meh"}); r.Status != 409 {
		t.Fatalf("IGNORED without justification: %d", r.Status)
	}
	r := c.patch(fmt.Sprintf("/api/v1/issues/%d", id), writer, map[string]any{"status": "FIXED_PENDING_VERIFY", "note": "parameterized query"})
	if r.Status != 200 || r.Map(t)["status"] != "FIXED_PENDING_VERIFY" || !strings.HasPrefix(r.Map(t)["resolved_by"].(string), "key:csk_") {
		t.Fatalf("mark fixed: %d %s", r.Status, r.Body)
	}
	// Un humain peut résoudre manuellement.
	if r := c.patch(fmt.Sprintf("/api/v1/issues/%d", id), tok, map[string]any{"status": "RESOLVED"}); r.Status != 200 {
		t.Fatalf("human RESOLVED: %d", r.Status)
	}

	// Filtres.
	c.get("/api/v1/issues?category=SCA", reader).JSON(t, &page)
	if len(page.Items) != 1 || page.Items[0]["package"] != "flask" {
		t.Fatalf("category filter: %v", page.Items)
	}
	c.get("/api/v1/issues?status=OPEN,REOPENED&severity=high", reader).JSON(t, &page)
	if len(page.Items) != 1 || page.Items[0]["category"] != "SECRET" {
		t.Fatalf("status+severity filter: %v", page.Items)
	}
	if r := c.get("/api/v1/issues?severity=EXTREME", reader); r.Status != 422 {
		t.Fatalf("bad enum: %d", r.Status)
	}

	// Bulk.
	ids := []int64{}
	c.get("/api/v1/issues", reader).JSON(t, &page)
	for _, it := range page.Items {
		ids = append(ids, int64(it["id"].(float64)))
	}
	res := c.post("/api/v1/issues/bulk", writer, map[string]any{"ids": ids, "status": "IN_PROGRESS"}).Map(t)
	if len(res["updated"].([]any)) != 3 {
		t.Fatalf("bulk: %v", res)
	}
}

func TestIssuesPaginationAndIsolation(t *testing.T) {
	c := newClient(t)
	alice, bob := c.login("alice@example.com"), c.login("bob@example.com")
	pa, pb := c.project(alice, "a"), c.project(alice, "b")
	var fs []map[string]any
	for i := 1; i <= 7; i++ {
		sev := []string{"LOW", "HIGH", "CRITICAL"}[i%3]
		fs = append(fs, finding(fmt.Sprintf("R%d", i), "x.py", i, sev, nil))
	}
	c.sync(alice, pa, fs...)
	c.sync(alice, pb, finding("OTHER", "y.py", 1, "LOW", nil))

	seen := map[float64]bool{}
	cursor := ""
	pages := 0
	lastRank := -1
	rank := map[string]int{"CRITICAL": 0, "HIGH": 1, "MEDIUM": 2, "LOW": 3}
	for {
		var page struct {
			Items []map[string]any `json:"items"`
			Next  *string          `json:"next_cursor"`
		}
		url := fmt.Sprintf("/api/v1/issues?project_id=%d&limit=3", pa)
		if cursor != "" {
			url += "&cursor=" + cursor
		}
		c.get(url, alice).JSON(t, &page)
		pages++
		for _, it := range page.Items {
			if seen[it["id"].(float64)] {
				t.Fatalf("duplicate across pages: %v", it)
			}
			seen[it["id"].(float64)] = true
			if r := rank[it["severity"].(string)]; r < lastRank {
				t.Fatalf("not sorted by severity")
			} else {
				lastRank = r
			}
		}
		if page.Next == nil {
			break
		}
		cursor = *page.Next
	}
	if len(seen) != 7 || pages != 3 {
		t.Fatalf("seen=%d pages=%d", len(seen), pages)
	}

	// Bob ne voit rien et ne peut rien modifier.
	bobKey := c.key(bob, []string{"issues:read", "issues:write"}, nil)
	var page struct {
		Items []map[string]any `json:"items"`
	}
	c.get("/api/v1/issues", bobKey).JSON(t, &page)
	if len(page.Items) != 0 {
		t.Fatalf("bob sees %v", page.Items)
	}
	var id float64
	for k := range seen {
		id = k
		break
	}
	if r := c.get(fmt.Sprintf("/api/v1/issues/%d", int64(id)), bobKey); r.Status != 404 {
		t.Fatalf("bob get: %d", r.Status)
	}
	if r := c.patch(fmt.Sprintf("/api/v1/issues/%d", int64(id)), bobKey, map[string]any{"status": "IN_PROGRESS"}); r.Status != 404 {
		t.Fatalf("bob patch: %d", r.Status)
	}

	// Clé restreinte au projet b.
	scoped := c.key(alice, []string{"issues:read", "sync:write"}, &pb)
	c.get("/api/v1/issues", scoped).JSON(t, &page)
	if len(page.Items) != 1 || page.Items[0]["rule_id"] != "OTHER" {
		t.Fatalf("scoped key: %v", page.Items)
	}
	if r := c.post("/api/v1/sync/submit", scoped, map[string]any{"project_id": pa, "findings": []any{}}); r.Status != 404 {
		t.Fatalf("scoped key sync on other project: %d", r.Status)
	}
	if r := c.post("/api/v1/keys", alice, map[string]any{"name": "x", "scopes": []string{"admin"}}); r.Status != 422 {
		t.Fatalf("invalid scope: %d", r.Status)
	}
}
