package test

import (
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"testing"
)

// Moteur différentiel : parité v1 + cas v2 (empreintes, réouverture, statuts manuels).

func fp(s string) string {
	h := sha256.Sum256([]byte(s))
	return hex.EncodeToString(h[:])
}

func statuses(t *testing.T, c *client, tok string, pid int64) map[string]string {
	t.Helper()
	var vulns []map[string]any
	c.get(fmt.Sprintf("/api/v1/projects/%d/vulnerabilities", pid), tok).JSON(t, &vulns)
	out := map[string]string{}
	for _, v := range vulns {
		out[v["rule_id"].(string)] = v["status"].(string)
	}
	return out
}

func TestSyncDiffParity(t *testing.T) {
	c := newClient(t)
	tok := c.login("sync@example.com")
	pid := c.project(tok, "demo")
	r1, r2 := finding("R1", "src/a.py", 10, "HIGH", nil), finding("R2", "src/a.py", 10, "HIGH", nil)

	first := c.sync(tok, pid, r1, r2)
	if num(first, "created") != 2 || num(first, "touched") != 0 || num(first, "open_total") != 2 {
		t.Fatalf("first: %v", first)
	}
	second := c.sync(tok, pid, r1, r2)
	if num(second, "created") != 0 || num(second, "touched") != 2 || second["scan_id"] == first["scan_id"] {
		t.Fatalf("second: %v", second)
	}
	third := c.sync(tok, pid, r2, finding("R3", "src/a.py", 10, "HIGH", nil))
	if num(third, "created") != 1 || num(third, "touched") != 1 || num(third, "resolved") != 1 || num(third, "open_total") != 2 {
		t.Fatalf("third: %v", third)
	}
	if got := statuses(t, c, tok, pid); got["R1"] != "RESOLVED" || got["R2"] != "OPEN" || got["R3"] != "OPEN" {
		t.Fatalf("statuses: %v", got)
	}
}

func TestIdentityKeyIncludesLineNumber(t *testing.T) {
	c := newClient(t)
	tok := c.login("line@example.com")
	pid := c.project(tok, "demo")
	res := c.sync(tok, pid, finding("R1", "a.py", 10, "HIGH", nil), finding("R1", "a.py", 20, "HIGH", nil))
	if num(res, "created") != 2 {
		t.Fatalf("%v", res)
	}
}

func TestEmptyResubmitResolvesEverything(t *testing.T) {
	c := newClient(t)
	tok := c.login("empty@example.com")
	pid := c.project(tok, "demo")
	c.sync(tok, pid, finding("R1", "a.py", 1, "CRITICAL", nil))
	res := c.sync(tok, pid)
	if num(res, "resolved") != 1 || num(res, "open_total") != 0 || num(res, "score") != 100 {
		t.Fatalf("%v", res)
	}
}

// Bug v1 : une faille RESOLVED qui réapparaît violait la contrainte d'unicité.
func TestResolvedFindingIsReopened(t *testing.T) {
	c := newClient(t)
	tok := c.login("reopen@example.com")
	pid := c.project(tok, "demo")
	f := finding("R1", "a.py", 3, "HIGH", nil)
	c.sync(tok, pid, f)
	c.sync(tok, pid)
	res := c.sync(tok, pid, f)
	if num(res, "reopened") != 1 || num(res, "created") != 0 || num(res, "open_total") != 1 {
		t.Fatalf("%v", res)
	}
	if got := statuses(t, c, tok, pid); got["R1"] != "REOPENED" {
		t.Fatalf("%v", got)
	}
}

// Une empreinte stable suit la faille quand le code se décale.
func TestFingerprintSurvivesLineShift(t *testing.T) {
	c := newClient(t)
	tok := c.login("fp@example.com")
	pid := c.project(tok, "demo")
	print := fp("R1|a.py|eval(x)|0")
	c.sync(tok, pid, finding("R1", "a.py", 10, "HIGH", map[string]any{"fingerprint": print}))
	res := c.sync(tok, pid, finding("R1", "a.py", 14, "HIGH", map[string]any{"fingerprint": print, "end_line": 15}))
	if num(res, "touched") != 1 || num(res, "created") != 0 || num(res, "resolved") != 0 {
		t.Fatalf("%v", res)
	}
	var vulns []map[string]any
	c.get(fmt.Sprintf("/api/v1/projects/%d/vulnerabilities", pid), tok).JSON(t, &vulns)
	if len(vulns) != 1 || num(vulns[0], "line_number") != 14 || num(vulns[0], "end_line") != 15 {
		t.Fatalf("%v", vulns)
	}
}

// Une ligne historique sans empreinte est adoptée par un client v2.
func TestLegacyRowUpgradedToFingerprint(t *testing.T) {
	c := newClient(t)
	tok := c.login("upgrade@example.com")
	pid := c.project(tok, "demo")
	c.sync(tok, pid, finding("R1", "a.py", 10, "HIGH", nil))
	res := c.sync(tok, pid, finding("R1", "a.py", 10, "HIGH", map[string]any{"fingerprint": fp("x")}))
	if num(res, "touched") != 1 || num(res, "created") != 0 {
		t.Fatalf("%v", res)
	}
}

func TestDuplicateFindingsInPayloadAreDeduplicated(t *testing.T) {
	c := newClient(t)
	tok := c.login("dup@example.com")
	pid := c.project(tok, "demo")
	f := finding("R1", "a.py", 10, "HIGH", nil)
	res := c.sync(tok, pid, f, f)
	if num(res, "created") != 1 || num(res, "open_total") != 1 {
		t.Fatalf("%v", res)
	}
}

func issueID(t *testing.T, c *client, tok string, pid int64, rule string) int64 {
	t.Helper()
	var vulns []map[string]any
	c.get(fmt.Sprintf("/api/v1/projects/%d/vulnerabilities", pid), tok).JSON(t, &vulns)
	for _, v := range vulns {
		if v["rule_id"] == rule {
			return int64(v["id"].(float64))
		}
	}
	t.Fatalf("no issue %s", rule)
	return 0
}

func TestIgnoredIssueStaysIgnoredAndLeavesScore(t *testing.T) {
	c := newClient(t)
	tok := c.login("ignore@example.com")
	pid := c.project(tok, "demo")
	f := finding("R1", "a.py", 1, "CRITICAL", nil)
	c.sync(tok, pid, f)
	id := issueID(t, c, tok, pid, "R1")
	if r := c.patch(fmt.Sprintf("/api/v1/issues/%d", id), tok, map[string]any{"status": "IGNORED", "note": "test fixture, never deployed"}); r.Status != 200 {
		t.Fatalf("ignore: %d %s", r.Status, r.Body)
	}
	res := c.sync(tok, pid, f)
	if num(res, "open_total") != 0 || num(res, "score") != 100 {
		t.Fatalf("%v", res)
	}
	if got := statuses(t, c, tok, pid); got["R1"] != "IGNORED" {
		t.Fatalf("%v", got)
	}
}

// Workflow agent : FIXED_PENDING_VERIFY -> RESOLVED si le scan ne la voit plus,
// REOPENED si elle est toujours là.
func TestFixedPendingVerifyIsConfirmedOrReopenedByScan(t *testing.T) {
	c := newClient(t)
	tok := c.login("verify@example.com")
	pid := c.project(tok, "demo")
	a, b := finding("RA", "a.py", 1, "HIGH", nil), finding("RB", "b.py", 1, "HIGH", nil)
	c.sync(tok, pid, a, b)
	for _, rule := range []string{"RA", "RB"} {
		id := issueID(t, c, tok, pid, rule)
		if r := c.patch(fmt.Sprintf("/api/v1/issues/%d", id), tok, map[string]any{"status": "FIXED_PENDING_VERIFY", "note": "fixed"}); r.Status != 200 {
			t.Fatalf("mark fixed: %d %s", r.Status, r.Body)
		}
	}
	// FIXED_PENDING_VERIFY compte toujours dans le score avant vérification.
	m := c.get(fmt.Sprintf("/api/v1/projects/%d/metrics", pid), tok).Map(t)
	if num(m, "score") != 90 {
		t.Fatalf("metrics: %v", m)
	}
	res := c.sync(tok, pid, b) // RA corrigée pour de vrai, RB non
	if num(res, "resolved") != 1 || num(res, "reopened") != 1 {
		t.Fatalf("%v", res)
	}
	if got := statuses(t, c, tok, pid); got["RA"] != "RESOLVED" || got["RB"] != "REOPENED" {
		t.Fatalf("%v", got)
	}
}
