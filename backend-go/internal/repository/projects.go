package repository

import (
	"context"
	"strings"

	"github.com/jackc/pgx/v5"

	"codeshield/backend/internal/domain"
)

const projectCols = `id, owner_id, name, repo_url, created_at`

func scanProject(row pgx.Row) (domain.Project, error) {
	var p domain.Project
	err := row.Scan(&p.ID, &p.OwnerID, &p.Name, &p.RepoURL, &p.CreatedAt)
	return p, err
}

func (q *Queries) CreateProject(ctx context.Context, ownerID int64, name string, repoURL *string) (domain.Project, error) {
	return scanProject(q.db.QueryRow(ctx,
		`INSERT INTO projects (owner_id, name, repo_url) VALUES ($1, $2, $3) RETURNING `+projectCols,
		ownerID, strings.TrimSpace(name), repoURL))
}

func (q *Queries) ListProjects(ctx context.Context, ownerID int64) ([]domain.Project, error) {
	rows, err := q.db.Query(ctx,
		`SELECT `+projectCols+` FROM projects WHERE owner_id = $1 ORDER BY id DESC`, ownerID)
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, func(r pgx.CollectableRow) (domain.Project, error) { return scanProject(r) })
}

func (q *Queries) GetOwnedProject(ctx context.Context, id, ownerID int64) (domain.Project, error) {
	p, err := scanProject(q.db.QueryRow(ctx,
		`SELECT `+projectCols+` FROM projects WHERE id = $1 AND owner_id = $2`, id, ownerID))
	return p, notFound(err)
}

func (q *Queries) DeleteProject(ctx context.Context, id int64) error {
	_, err := q.db.Exec(ctx, `DELETE FROM projects WHERE id = $1`, id)
	return err
}

func (q *Queries) InsertScan(ctx context.Context, projectID int64, count int, commit, branch *string) (domain.Scan, error) {
	s := domain.Scan{ProjectID: projectID, FindingsCount: count, CommitSHA: commit, Branch: branch}
	err := q.db.QueryRow(ctx,
		`INSERT INTO scans (project_id, findings_count, commit_sha, branch) VALUES ($1, $2, $3, $4)
		 RETURNING id, created_at`, projectID, count, commit, branch).Scan(&s.ID, &s.CreatedAt)
	return s, err
}

func (q *Queries) ListScans(ctx context.Context, projectID int64, limit int) ([]domain.Scan, error) {
	rows, err := q.db.Query(ctx,
		`SELECT id, project_id, findings_count, commit_sha, branch, created_at
		 FROM scans WHERE project_id = $1 ORDER BY id DESC LIMIT $2`, projectID, limit)
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, func(r pgx.CollectableRow) (domain.Scan, error) {
		var s domain.Scan
		err := r.Scan(&s.ID, &s.ProjectID, &s.FindingsCount, &s.CommitSHA, &s.Branch, &s.CreatedAt)
		return s, err
	})
}

func (q *Queries) CountScans(ctx context.Context, projectID int64) (int, error) {
	var n int
	err := q.db.QueryRow(ctx, `SELECT count(*) FROM scans WHERE project_id = $1`, projectID).Scan(&n)
	return n, err
}
