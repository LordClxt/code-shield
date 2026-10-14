package service

import (
	"testing"

	"codeshield/backend/internal/domain"
)

// Parité backend/tests/test_score_service.py.
func TestComputeScore(t *testing.T) {
	cases := []struct {
		counts map[domain.Severity]int
		score  int
		grade  string
	}{
		{map[domain.Severity]int{}, 100, "A"},
		{map[domain.Severity]int{domain.SeverityHigh: 1}, 95, "A"},
		{map[domain.Severity]int{domain.SeverityCritical: 1, domain.SeverityHigh: 1}, 85, "B"},
		{map[domain.Severity]int{domain.SeverityCritical: 3, domain.SeverityMedium: 3}, 64, "C"},
		{map[domain.Severity]int{domain.SeverityCritical: 5, domain.SeverityHigh: 2}, 40, "D"},
		{map[domain.Severity]int{domain.SeverityCritical: 20}, 0, "F"},
		{map[domain.Severity]int{domain.SeverityLow: 5}, 98, "A"}, // 2.5 -> 2 (round half even)
		{map[domain.Severity]int{domain.SeverityLow: 7}, 96, "A"}, // 3.5 -> 4
	}
	for _, c := range cases {
		s, g := ComputeScore(c.counts)
		if s != c.score || g != c.grade {
			t.Errorf("%v: got %d/%s want %d/%s", c.counts, s, g, c.score, c.grade)
		}
	}
}

func TestCheckTransition(t *testing.T) {
	note := "false positive: test fixture"
	short := "nope"
	if CheckTransition(domain.StatusResolved, nil, false) == nil {
		t.Error("agent RESOLVED allowed")
	}
	if CheckTransition(domain.StatusResolved, nil, true) != nil {
		t.Error("human RESOLVED refused")
	}
	if CheckTransition(domain.StatusReopened, nil, true) == nil {
		t.Error("REOPENED allowed")
	}
	if CheckTransition(domain.StatusIgnored, &short, true) == nil {
		t.Error("short justification allowed")
	}
	if CheckTransition(domain.StatusIgnored, &note, false) != nil {
		t.Error("justified IGNORED refused")
	}
}

func TestCursorRoundTrip(t *testing.T) {
	c := EncodeCursor(domain.SeverityMedium, 42)
	r, id, err := DecodeCursor(c)
	if err != nil || r != 2 || id != 42 {
		t.Fatalf("%d %d %v", r, id, err)
	}
	if _, _, err := DecodeCursor("!!"); err == nil {
		t.Fatal("bad cursor accepted")
	}
}

func TestExplainCategories(t *testing.T) {
	pkg, fixed := "lodash", "4.17.21"
	cases := map[string]domain.Vulnerability{
		"Injection SQL":                                 {RuleID: "custom.rule", Category: domain.CategorySAST, CWE: []string{"CWE-89"}},
		"Secret exposé — clé cloud AWS":                 {RuleID: "aws-access-token", Category: domain.CategorySecret},
		"Dépendance vulnérable (SCA)":                   {RuleID: "CVE-2021-23337", Category: domain.CategorySCA, Package: &pkg, FixedVersion: &fixed},
		"Mauvaise configuration d'infrastructure (IaC)": {RuleID: "CKV_AWS_20", Category: domain.CategoryIaC},
		"Désérialisation non sûre":                      {RuleID: "B301", Category: domain.CategorySAST},
	}
	for want, v := range cases {
		if got := Explain(&v).Category; got != want {
			t.Errorf("%s: got %q want %q", v.RuleID, got, want)
		}
	}
}
