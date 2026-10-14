package repository

import (
	"context"
	"strings"

	"github.com/jackc/pgx/v5"

	"codeshield/backend/internal/domain"
)

const apiKeyCols = `id, user_id, name, key_hash, prefix, scopes, project_id, revoked, last_used_at, created_at`

func scanAPIKey(row pgx.Row) (domain.APIKey, error) {
	var k domain.APIKey
	var scopes []string
	err := row.Scan(&k.ID, &k.UserID, &k.Name, &k.KeyHash, &k.Prefix, &scopes,
		&k.ProjectID, &k.Revoked, &k.LastUsedAt, &k.CreatedAt)
	for _, s := range scopes {
		k.Scopes = append(k.Scopes, domain.Scope(s))
	}
	return k, err
}

func (q *Queries) CreateAPIKey(ctx context.Context, userID int64, name, hash, prefix string,
	scopes []domain.Scope, projectID *int64) (domain.APIKey, error) {
	raw := make([]string, len(scopes))
	for i, s := range scopes {
		raw[i] = string(s)
	}
	return scanAPIKey(q.db.QueryRow(ctx,
		`INSERT INTO api_keys (user_id, name, key_hash, prefix, scopes, project_id)
		 VALUES ($1, $2, $3, $4, $5, $6) RETURNING `+apiKeyCols,
		userID, strings.TrimSpace(name), hash, prefix, raw, projectID))
}

func (q *Queries) ListAPIKeys(ctx context.Context, userID int64) ([]domain.APIKey, error) {
	rows, err := q.db.Query(ctx,
		`SELECT `+apiKeyCols+` FROM api_keys WHERE user_id = $1 ORDER BY id DESC`, userID)
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, func(r pgx.CollectableRow) (domain.APIKey, error) { return scanAPIKey(r) })
}

func (q *Queries) APIKeyByHash(ctx context.Context, hash string) (domain.APIKey, error) {
	k, err := scanAPIKey(q.db.QueryRow(ctx, `SELECT `+apiKeyCols+` FROM api_keys WHERE key_hash = $1`, hash))
	return k, notFound(err)
}

// RevokeAPIKey retourne ErrNotFound si la clé n'appartient pas à l'utilisateur.
func (q *Queries) RevokeAPIKey(ctx context.Context, id, userID int64) error {
	tag, err := q.db.Exec(ctx, `UPDATE api_keys SET revoked = true WHERE id = $1 AND user_id = $2`, id, userID)
	if err != nil {
		return err
	}
	if tag.RowsAffected() == 0 {
		return ErrNotFound
	}
	return nil
}

func (q *Queries) TouchAPIKey(ctx context.Context, id int64) error {
	_, err := q.db.Exec(ctx, `UPDATE api_keys SET last_used_at = now() WHERE id = $1`, id)
	return err
}
