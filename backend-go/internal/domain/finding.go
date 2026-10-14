package domain

import (
	"fmt"
	"regexp"
	"unicode/utf8"
)

const MaxFindingsPerSync = 5000

// Finding : contrat Desktop -> Backend. Le décodage se fait avec
// DisallowUnknownFields : tout champ de code (`snippet`, `source_code`…)
// est rejeté en 422 (directive Sécurité Intellectuelle).
type Finding struct {
	Tool             Tool     `json:"tool"`
	RuleID           string   `json:"rule_id"`
	FilePath         string   `json:"file_path"`
	LineNumber       int      `json:"line_number"`
	EndLine          *int     `json:"end_line,omitempty"`
	Column           *int     `json:"column,omitempty"`
	EndColumn        *int     `json:"end_column,omitempty"`
	Severity         Severity `json:"severity"`
	Title            string   `json:"title"`
	Message          *string  `json:"message,omitempty"`
	Category         Category `json:"category,omitempty"`
	CWE              []string `json:"cwe,omitempty"`
	OWASP            []string `json:"owasp,omitempty"`
	RemediationKey   *string  `json:"remediation_key,omitempty"`
	Package          *string  `json:"package,omitempty"`
	InstalledVersion *string  `json:"installed_version,omitempty"`
	FixedVersion     *string  `json:"fixed_version,omitempty"`
	Fingerprint      *string  `json:"fingerprint,omitempty"`
}

type SyncSubmitRequest struct {
	ProjectID int64     `json:"project_id"`
	CommitSHA *string   `json:"commit_sha,omitempty"`
	Branch    *string   `json:"branch,omitempty"`
	Findings  []Finding `json:"findings"`
}

func (f *Finding) LegacyKey() LegacyKey {
	return LegacyKey{f.Tool, f.RuleID, f.FilePath, f.LineNumber}
}

var (
	fingerprintRe = regexp.MustCompile(`^[0-9a-f]{64}$`)
	cweRe         = regexp.MustCompile(`^CWE-[0-9]{1,6}$`)
	shaRe         = regexp.MustCompile(`^[0-9a-fA-F]{7,64}$`)
)

// FieldError décrit une erreur de validation au format proche de FastAPI.
type FieldError struct {
	Loc []any  `json:"loc"`
	Msg string `json:"msg"`
}

func (e FieldError) Error() string { return fmt.Sprintf("%v: %s", e.Loc, e.Msg) }

func strLen(field string, v string, min, max int, loc []any) *FieldError {
	n := utf8.RuneCountInString(v)
	if n < min || n > max {
		return &FieldError{Loc: append(loc, field), Msg: fmt.Sprintf("length must be between %d and %d", min, max)}
	}
	return nil
}

func optStrLen(field string, v *string, max int, loc []any) *FieldError {
	if v == nil {
		return nil
	}
	return strLen(field, *v, 0, max, loc)
}

func intRange(field string, v *int, min, max int, loc []any) *FieldError {
	if v == nil {
		return nil
	}
	if *v < min || *v > max {
		return &FieldError{Loc: append(loc, field), Msg: fmt.Sprintf("must be between %d and %d", min, max)}
	}
	return nil
}

// Normalize applique les valeurs par défaut (catégorie déduite de l'outil).
func (f *Finding) Normalize() {
	if f.Category == "" {
		f.Category = f.Tool.DefaultCategory()
	}
}

func (f *Finding) Validate(idx int) []FieldError {
	loc := []any{"body", "findings", idx}
	var errs []FieldError
	add := func(e *FieldError) {
		if e != nil {
			errs = append(errs, *e)
		}
	}
	if !f.Tool.Valid() {
		add(&FieldError{Loc: append(loc, "tool"), Msg: "invalid tool"})
	}
	if !f.Severity.Valid() {
		add(&FieldError{Loc: append(loc, "severity"), Msg: "invalid severity"})
	}
	if f.Category != "" && !f.Category.Valid() {
		add(&FieldError{Loc: append(loc, "category"), Msg: "invalid category"})
	}
	add(strLen("rule_id", f.RuleID, 1, 320, loc))
	add(strLen("file_path", f.FilePath, 1, 1024, loc))
	add(strLen("title", f.Title, 1, 500, loc))
	add(optStrLen("message", f.Message, 5000, loc))
	add(optStrLen("remediation_key", f.RemediationKey, 200, loc))
	add(optStrLen("package", f.Package, 500, loc))
	add(optStrLen("installed_version", f.InstalledVersion, 200, loc))
	add(optStrLen("fixed_version", f.FixedVersion, 200, loc))
	ln := f.LineNumber
	add(intRange("line_number", &ln, 1, 10_000_000, loc))
	add(intRange("end_line", f.EndLine, 1, 10_000_000, loc))
	add(intRange("column", f.Column, 1, 100_000, loc))
	add(intRange("end_column", f.EndColumn, 1, 100_000, loc))
	if f.EndLine != nil && *f.EndLine < f.LineNumber {
		add(&FieldError{Loc: append(loc, "end_line"), Msg: "must be >= line_number"})
	}
	if f.Fingerprint != nil && !fingerprintRe.MatchString(*f.Fingerprint) {
		add(&FieldError{Loc: append(loc, "fingerprint"), Msg: "must be a lowercase sha256 hex digest"})
	}
	if len(f.CWE) > 20 {
		add(&FieldError{Loc: append(loc, "cwe"), Msg: "at most 20 items"})
	}
	for i, c := range f.CWE {
		if !cweRe.MatchString(c) {
			add(&FieldError{Loc: append(loc, "cwe", i), Msg: "must match CWE-<number>"})
		}
	}
	if len(f.OWASP) > 20 {
		add(&FieldError{Loc: append(loc, "owasp"), Msg: "at most 20 items"})
	}
	for i, o := range f.OWASP {
		add(strLen(fmt.Sprint(i), o, 1, 120, append(loc, "owasp")))
	}
	return errs
}

func (r *SyncSubmitRequest) Validate() []FieldError {
	var errs []FieldError
	if r.ProjectID <= 0 {
		errs = append(errs, FieldError{Loc: []any{"body", "project_id"}, Msg: "field required"})
	}
	if r.Findings == nil {
		errs = append(errs, FieldError{Loc: []any{"body", "findings"}, Msg: "field required"})
	}
	if len(r.Findings) > MaxFindingsPerSync {
		errs = append(errs, FieldError{Loc: []any{"body", "findings"}, Msg: fmt.Sprintf("at most %d items", MaxFindingsPerSync)})
		return errs
	}
	if r.CommitSHA != nil && !shaRe.MatchString(*r.CommitSHA) {
		errs = append(errs, FieldError{Loc: []any{"body", "commit_sha"}, Msg: "must be a git sha"})
	}
	if e := optStrLen("branch", r.Branch, 255, []any{"body"}); e != nil {
		errs = append(errs, *e)
	}
	for i := range r.Findings {
		errs = append(errs, r.Findings[i].Validate(i)...)
	}
	return errs
}
