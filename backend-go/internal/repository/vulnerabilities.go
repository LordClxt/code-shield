package repository

import (
	"context"
	"fmt"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"

	"codeshield/backend/internal/domain"
)

const vulnCols = `v.id, v.project_id, v.last_scan_id, v.tool, v.rule_id, v.file_path, v.line_number,
	v.end_line, v.start_column, v.end_column, v.severity, v.title, v.message, v.category, v.cwe, v.owasp,
	v.remediation_key, v.package_name, v.installed_version, v.fixed_version, v.fingerprint, v.status,
	v.first_seen, v.last_seen, v.resolved_at, v.resolved_by, v.resolution_note`

func scanVulnInto(row pgx.Row, extra ...any) (domain.Vulnerability, error) {
	var v domain.Vulnerability
	dest := []any{&v.ID, &v.ProjectID, &v.LastScanID, &v.Tool, &v.RuleID, &v.FilePath, &v.LineNumber,
		&v.EndLine, &v.Column, &v.EndColumn, &v.Severity, &v.Title, &v.Message, &v.Category, &v.CWE, &v.OWASP,
		&v.RemediationKey, &v.Package, &v.InstalledVersion, &v.FixedVersion, &v.Fingerprint, &v.Status,
		&v.FirstSeen, &v.LastSeen, &v.ResolvedAt, &v.ResolvedBy, &v.ResolutionNote}
	err := row.Scan(append(dest, extra...)...)
	if v.CWE == nil {
		v.CWE = []string{}
	}
	if v.OWASP == nil {
		v.OWASP = []string{}
	}
	return v, err
}

func collectVulns(rows pgx.Rows, err error) ([]domain.Vulnerability, error) {
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, func(r pgx.CollectableRow) (domain.Vulnerability, error) { return scanVulnInto(r) })
}

// LockProject sérialise les synchronisations concurrentes d'un même projet.
func (q *Queries) LockProject(ctx context.Context, projectID int64) error {
	_, err := q.db.Exec(ctx, `SELECT 1 FROM projects WHERE id = $1 FOR UPDATE`, projectID)
	return err
}

// AllProjectVulns charge toutes les failles du projet (tous statuts) pour le diff.
func (q *Queries) AllProjectVulns(ctx context.Context, projectID int64) ([]domain.Vulnerability, error) {
	rows, err := q.db.Query(ctx, `SELECT `+vulnCols+` FROM vulnerabilities v WHERE v.project_id = $1 ORDER BY v.id`, projectID)
	return collectVulns(rows, err)
}

func (q *Queries) InsertVuln(ctx context.Context, v *domain.Vulnerability) error {
	return q.db.QueryRow(ctx, `INSERT INTO vulnerabilities (
		project_id, last_scan_id, tool, rule_id, file_path, line_number, end_line, start_column, end_column,
		severity, title, message, category, cwe, owasp, remediation_key, package_name, installed_version,
		fixed_version, fingerprint, status, first_seen, last_seen)
		VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20,$21,$22,$22)
		RETURNING id`,
		v.ProjectID, v.LastScanID, v.Tool, v.RuleID, v.FilePath, v.LineNumber, v.EndLine, v.Column, v.EndColumn,
		v.Severity, v.Title, v.Message, v.Category, v.CWE, v.OWASP, v.RemediationKey, v.Package,
		v.InstalledVersion, v.FixedVersion, v.Fingerprint, v.Status, v.FirstSeen).Scan(&v.ID)
}

// UpdateVulnFromScan réécrit les métadonnées mutables après un match de synchro.
func (q *Queries) UpdateVulnFromScan(ctx context.Context, v *domain.Vulnerability) error {
	_, err := q.db.Exec(ctx, `UPDATE vulnerabilities SET
		last_scan_id=$2, rule_id=$3, file_path=$4, line_number=$5, end_line=$6, start_column=$7, end_column=$8,
		severity=$9, title=$10, message=$11, category=$12, cwe=$13, owasp=$14, remediation_key=$15,
		package_name=$16, installed_version=$17, fixed_version=$18, fingerprint=$19, status=$20,
		last_seen=$21, resolved_at=$22, resolved_by=$23, resolution_note=$24
		WHERE id=$1`,
		v.ID, v.LastScanID, v.RuleID, v.FilePath, v.LineNumber, v.EndLine, v.Column, v.EndColumn,
		v.Severity, v.Title, v.Message, v.Category, v.CWE, v.OWASP, v.RemediationKey,
		v.Package, v.InstalledVersion, v.FixedVersion, v.Fingerprint, v.Status,
		v.LastSeen, v.ResolvedAt, v.ResolvedBy, v.ResolutionNote)
	return err
}

// ResolveByScan passe en RESOLVED les failles absentes du nouveau scan.
func (q *Queries) ResolveByScan(ctx context.Context, ids []int64, at time.Time) error {
	if len(ids) == 0 {
		return nil
	}
	_, err := q.db.Exec(ctx, `UPDATE vulnerabilities
		SET status='RESOLVED', last_seen=$2, resolved_at=$2, resolved_by=COALESCE(resolved_by, 'scan')
		WHERE id = ANY($1)`, ids, at)
	return err
}

type VulnFilter struct {
	Status   *domain.Status
	Severity *domain.Severity
}

func (q *Queries) ListProjectVulns(ctx context.Context, projectID int64, f VulnFilter) ([]domain.Vulnerability, error) {
	sql := `SELECT ` + vulnCols + ` FROM vulnerabilities v WHERE v.project_id = $1`
	args := []any{projectID}
	if f.Status != nil {
		args = append(args, *f.Status)
		sql += fmt.Sprintf(" AND v.status = $%d", len(args))
	}
	if f.Severity != nil {
		args = append(args, *f.Severity)
		sql += fmt.Sprintf(" AND v.severity = $%d", len(args))
	}
	rows, err := q.db.Query(ctx, sql+` ORDER BY v.id DESC`, args...)
	return collectVulns(rows, err)
}

// CountActiveBySeverity : failles comptées dans le score (statuts actifs).
func (q *Queries) CountActiveBySeverity(ctx context.Context, projectID int64) (map[domain.Severity]int, error) {
	active := make([]string, len(domain.ActiveStatuses))
	for i, s := range domain.ActiveStatuses {
		active[i] = string(s)
	}
	rows, err := q.db.Query(ctx, `SELECT severity, count(*) FROM vulnerabilities
		WHERE project_id = $1 AND status = ANY($2) GROUP BY severity`, projectID, active)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := map[domain.Severity]int{}
	for _, s := range domain.Severities {
		out[s] = 0
	}
	for rows.Next() {
		var sev domain.Severity
		var n int
		if err := rows.Scan(&sev, &n); err != nil {
			return nil, err
		}
		out[sev] = n
	}
	return out, rows.Err()
}

func (q *Queries) CountByStatus(ctx context.Context, projectID int64, status domain.Status) (int, error) {
	var n int
	err := q.db.QueryRow(ctx, `SELECT count(*) FROM vulnerabilities WHERE project_id = $1 AND status = $2`,
		projectID, status).Scan(&n)
	return n, err
}

type TimelinePoint struct {
	Date     string `json:"date"`
	Opened   int    `json:"opened"`
	Resolved int    `json:"resolved"`
}

// Timeline : nouvelles failles vs failles corrigées par jour sur `days` jours.
func (q *Queries) Timeline(ctx context.Context, projectID int64, days int) ([]TimelinePoint, error) {
	rows, err := q.db.Query(ctx, `
		WITH d AS (
			SELECT generate_series((now() AT TIME ZONE 'UTC')::date - ($2::int - 1), (now() AT TIME ZONE 'UTC')::date, '1 day')::date AS day
		)
		SELECT to_char(d.day, 'YYYY-MM-DD'),
			(SELECT count(*) FROM vulnerabilities v WHERE v.project_id = $1 AND (v.first_seen AT TIME ZONE 'UTC')::date = d.day),
			(SELECT count(*) FROM vulnerabilities v WHERE v.project_id = $1 AND v.status = 'RESOLVED'
				AND (v.resolved_at AT TIME ZONE 'UTC')::date = d.day)
		FROM d ORDER BY d.day`, projectID, days)
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, func(r pgx.CollectableRow) (TimelinePoint, error) {
		var p TimelinePoint
		err := r.Scan(&p.Date, &p.Opened, &p.Resolved)
		return p, err
	})
}

// ---------------------------------------------------------------------------
// Issues (API agent / MCP) : vue transverse sur tous les projets d'un user.

type Issue struct {
	domain.Vulnerability
	ProjectName string `json:"project_name"`
}

type IssueFilter struct {
	OwnerID    int64
	ProjectID  *int64
	Statuses   []domain.Status
	Severities []domain.Severity
	Tools      []domain.Tool
	Categories []domain.Category
	FilePrefix string
	// Curseur : (rang de sévérité, id) du dernier élément de la page précédente.
	AfterRank *int
	AfterID   *int64
	Limit     int
}

const sevRankSQL = `CASE v.severity WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END`

func toStrings[T ~string](xs []T) []string {
	out := make([]string, len(xs))
	for i, x := range xs {
		out[i] = string(x)
	}
	return out
}

// ListIssues trie par gravité puis par id décroissant (les plus graves d'abord).
func (q *Queries) ListIssues(ctx context.Context, f IssueFilter) ([]Issue, error) {
	var where []string
	args := []any{f.OwnerID}
	where = append(where, "p.owner_id = $1")
	add := func(cond string, v any) {
		args = append(args, v)
		where = append(where, fmt.Sprintf(cond, len(args)))
	}
	if f.ProjectID != nil {
		add("v.project_id = $%d", *f.ProjectID)
	}
	if len(f.Statuses) > 0 {
		add("v.status = ANY($%d)", toStrings(f.Statuses))
	}
	if len(f.Severities) > 0 {
		add("v.severity = ANY($%d)", toStrings(f.Severities))
	}
	if len(f.Tools) > 0 {
		add("v.tool = ANY($%d)", toStrings(f.Tools))
	}
	if len(f.Categories) > 0 {
		add("v.category = ANY($%d)", toStrings(f.Categories))
	}
	if f.FilePrefix != "" {
		add("v.file_path LIKE $%d", escapeLike(f.FilePrefix)+"%")
	}
	if f.AfterRank != nil && f.AfterID != nil {
		args = append(args, *f.AfterRank, *f.AfterID)
		where = append(where, fmt.Sprintf("((%s) > $%d OR ((%s) = $%d AND v.id < $%d))",
			sevRankSQL, len(args)-1, sevRankSQL, len(args)-1, len(args)))
	}
	args = append(args, f.Limit)
	sql := `SELECT ` + vulnCols + `, p.name FROM vulnerabilities v JOIN projects p ON p.id = v.project_id
		WHERE ` + strings.Join(where, " AND ") +
		fmt.Sprintf(` ORDER BY %s, v.id DESC LIMIT $%d`, sevRankSQL, len(args))
	rows, err := q.db.Query(ctx, sql, args...)
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, func(r pgx.CollectableRow) (Issue, error) {
		var is Issue
		v, err := scanVulnInto(r, &is.ProjectName)
		is.Vulnerability = v
		return is, err
	})
}

func escapeLike(s string) string {
	return strings.NewReplacer(`\`, `\\`, `%`, `\%`, `_`, `\_`).Replace(s)
}

func (q *Queries) GetIssue(ctx context.Context, ownerID, id int64) (Issue, error) {
	var is Issue
	v, err := scanVulnInto(q.db.QueryRow(ctx, `SELECT `+vulnCols+`, p.name FROM vulnerabilities v
		JOIN projects p ON p.id = v.project_id WHERE v.id = $1 AND p.owner_id = $2`, id, ownerID), &is.ProjectName)
	is.Vulnerability = v
	return is, notFound(err)
}

type StatusChange struct {
	Status domain.Status
	Note   *string
	By     string
}

// SetIssuesStatus met à jour le statut des failles possédées par ownerID
// (et limitées à projectID si non nil). Retourne les ids réellement modifiés.
func (q *Queries) SetIssuesStatus(ctx context.Context, ownerID int64, projectID *int64, ids []int64, c StatusChange) ([]int64, error) {
	rows, err := q.db.Query(ctx, `UPDATE vulnerabilities v SET
			status = $3::text,
			resolution_note = COALESCE($4::text, v.resolution_note),
			resolved_by = CASE WHEN $3::text IN ('OPEN', 'IN_PROGRESS') THEN NULL ELSE $5::text END,
			resolved_at = CASE WHEN $3::text = 'RESOLVED' THEN now() WHEN $3::text IN ('OPEN', 'IN_PROGRESS') THEN NULL ELSE v.resolved_at END
		FROM projects p
		WHERE p.id = v.project_id AND p.owner_id = $1 AND v.id = ANY($2)
			AND ($6::bigint IS NULL OR v.project_id = $6)
		RETURNING v.id`, ownerID, ids, c.Status, c.Note, c.By, projectID)
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, pgx.RowTo[int64])
}
