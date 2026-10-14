package domain

import "time"

// Aucune entité ne porte de code source : uniquement des métadonnées de
// positionnement (fichier, lignes, règle) et des empreintes (hash).

type User struct {
	ID           int64     `json:"id"`
	Email        string    `json:"email"`
	PasswordHash string    `json:"-"`
	CreatedAt    time.Time `json:"-"`
}

type Project struct {
	ID        int64     `json:"id"`
	OwnerID   int64     `json:"-"`
	Name      string    `json:"name"`
	RepoURL   *string   `json:"repo_url"`
	CreatedAt time.Time `json:"created_at"`
}

type Scan struct {
	ID            int64     `json:"id"`
	ProjectID     int64     `json:"project_id"`
	FindingsCount int       `json:"findings_count"`
	CommitSHA     *string   `json:"commit_sha"`
	Branch        *string   `json:"branch"`
	CreatedAt     time.Time `json:"created_at"`
}

type APIKey struct {
	ID         int64      `json:"id"`
	UserID     int64      `json:"-"`
	Name       string     `json:"name"`
	KeyHash    string     `json:"-"`
	Prefix     string     `json:"prefix"`
	Scopes     []Scope    `json:"scopes"`
	ProjectID  *int64     `json:"project_id"`
	Revoked    bool       `json:"revoked"`
	LastUsedAt *time.Time `json:"last_used_at"`
	CreatedAt  time.Time  `json:"created_at"`
}

type Vulnerability struct {
	ID               int64      `json:"id"`
	ProjectID        int64      `json:"project_id"`
	LastScanID       *int64     `json:"last_scan_id"`
	Tool             Tool       `json:"tool"`
	RuleID           string     `json:"rule_id"`
	FilePath         string     `json:"file_path"`
	LineNumber       int        `json:"line_number"`
	EndLine          *int       `json:"end_line"`
	Column           *int       `json:"column"`
	EndColumn        *int       `json:"end_column"`
	Severity         Severity   `json:"severity"`
	Title            string     `json:"title"`
	Message          *string    `json:"message"`
	Category         Category   `json:"category"`
	CWE              []string   `json:"cwe"`
	OWASP            []string   `json:"owasp"`
	RemediationKey   *string    `json:"remediation_key"`
	Package          *string    `json:"package"`
	InstalledVersion *string    `json:"installed_version"`
	FixedVersion     *string    `json:"fixed_version"`
	Fingerprint      *string    `json:"fingerprint"`
	Status           Status     `json:"status"`
	FirstSeen        time.Time  `json:"first_seen"`
	LastSeen         time.Time  `json:"last_seen"`
	ResolvedAt       *time.Time `json:"resolved_at"`
	ResolvedBy       *string    `json:"resolved_by"`
	ResolutionNote   *string    `json:"resolution_note"`
}

// LegacyKey : clé d'identité v1 (outil, règle, fichier, ligne), utilisée quand
// aucune empreinte n'est disponible.
type LegacyKey struct {
	Tool     Tool
	RuleID   string
	FilePath string
	Line     int
}

func (v *Vulnerability) LegacyKey() LegacyKey {
	return LegacyKey{v.Tool, v.RuleID, v.FilePath, v.LineNumber}
}
