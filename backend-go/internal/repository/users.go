package repository

import (
	"context"
	"strings"

	"codeshield/backend/internal/domain"
)

func normEmail(e string) string { return strings.ToLower(strings.TrimSpace(e)) }

func (q *Queries) CreateUser(ctx context.Context, email, hash string) (domain.User, error) {
	u := domain.User{Email: normEmail(email), PasswordHash: hash}
	err := q.db.QueryRow(ctx,
		`INSERT INTO users (email, password_hash) VALUES ($1, $2) RETURNING id, created_at`,
		u.Email, hash).Scan(&u.ID, &u.CreatedAt)
	return u, err
}

func (q *Queries) UserByEmail(ctx context.Context, email string) (domain.User, error) {
	var u domain.User
	err := q.db.QueryRow(ctx,
		`SELECT id, email, password_hash, created_at FROM users WHERE email = $1`, normEmail(email)).
		Scan(&u.ID, &u.Email, &u.PasswordHash, &u.CreatedAt)
	return u, notFound(err)
}

func (q *Queries) UserByID(ctx context.Context, id int64) (domain.User, error) {
	var u domain.User
	err := q.db.QueryRow(ctx,
		`SELECT id, email, password_hash, created_at FROM users WHERE id = $1`, id).
		Scan(&u.ID, &u.Email, &u.PasswordHash, &u.CreatedAt)
	return u, notFound(err)
}
