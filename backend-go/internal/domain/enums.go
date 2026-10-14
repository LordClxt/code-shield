// Package domain contient les entités et enums partagés.
//
// Les valeurs DOIVENT rester alignées avec contracts/openapi.yaml, le desktop
// (desktop/shared/.../model) et le front-end (frontend/src/lib/types.ts).
package domain

import "slices"

type Severity string

const (
	SeverityCritical Severity = "CRITICAL"
	SeverityHigh     Severity = "HIGH"
	SeverityMedium   Severity = "MEDIUM"
	SeverityLow      Severity = "LOW"
)

var Severities = []Severity{SeverityCritical, SeverityHigh, SeverityMedium, SeverityLow}

func (s Severity) Valid() bool { return slices.Contains(Severities, s) }

// Rank : 0 = le plus grave. Sert au tri des issues pour les agents.
func (s Severity) Rank() int {
	if i := slices.Index(Severities, s); i >= 0 {
		return i
	}
	return len(Severities)
}

type Tool string

const (
	ToolSemgrep         Tool = "SEMGREP"
	ToolGitleaks        Tool = "GITLEAKS"
	ToolDependencyCheck Tool = "DEPENDENCY_CHECK"
	ToolTrivy           Tool = "TRIVY"
	ToolBandit          Tool = "BANDIT"
	ToolOSVScanner      Tool = "OSV_SCANNER"
	ToolCheckov         Tool = "CHECKOV"
)

var Tools = []Tool{ToolSemgrep, ToolGitleaks, ToolDependencyCheck, ToolTrivy, ToolBandit, ToolOSVScanner, ToolCheckov}

func (t Tool) Valid() bool { return slices.Contains(Tools, t) }

type Category string

const (
	CategorySAST   Category = "SAST"
	CategorySecret Category = "SECRET"
	CategorySCA    Category = "SCA"
	CategoryIaC    Category = "IAC"
)

var Categories = []Category{CategorySAST, CategorySecret, CategorySCA, CategoryIaC}

func (c Category) Valid() bool { return slices.Contains(Categories, c) }

// DefaultCategory : catégorie déduite de l'outil quand le client ne l'envoie pas
// (compat desktop v1).
func (t Tool) DefaultCategory() Category {
	switch t {
	case ToolGitleaks:
		return CategorySecret
	case ToolDependencyCheck, ToolOSVScanner:
		return CategorySCA
	case ToolCheckov:
		return CategoryIaC
	default:
		return CategorySAST
	}
}

type Status string

const (
	StatusOpen               Status = "OPEN"
	StatusInProgress         Status = "IN_PROGRESS"
	StatusFixedPendingVerify Status = "FIXED_PENDING_VERIFY"
	StatusResolved           Status = "RESOLVED"
	StatusIgnored            Status = "IGNORED"
	StatusReopened           Status = "REOPENED"
)

var Statuses = []Status{
	StatusOpen, StatusInProgress, StatusFixedPendingVerify,
	StatusResolved, StatusIgnored, StatusReopened,
}

func (s Status) Valid() bool { return slices.Contains(Statuses, s) }

// ActiveStatuses : failles encore présentes dans le code (comptées dans le score).
// FIXED_PENDING_VERIFY reste active tant qu'un rescan ne l'a pas confirmée.
var ActiveStatuses = []Status{StatusOpen, StatusInProgress, StatusReopened, StatusFixedPendingVerify}

func (s Status) Active() bool { return slices.Contains(ActiveStatuses, s) }

type Scope string

const (
	ScopeSyncWrite   Scope = "sync:write"
	ScopeIssuesRead  Scope = "issues:read"
	ScopeIssuesWrite Scope = "issues:write"
)

var Scopes = []Scope{ScopeSyncWrite, ScopeIssuesRead, ScopeIssuesWrite}

func (s Scope) Valid() bool { return slices.Contains(Scopes, s) }
