// Package repository isole tout le SQL (pgx). Aucune logique métier ici.
package repository

import (
	"context"
	"errors"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
)

var ErrNotFound = errors.New("not found")

// DBTX est satisfait par *pgxpool.Pool et pgx.Tx.
type DBTX interface {
	Exec(ctx context.Context, sql string, args ...any) (pgconn.CommandTag, error)
	Query(ctx context.Context, sql string, args ...any) (pgx.Rows, error)
	QueryRow(ctx context.Context, sql string, args ...any) pgx.Row
}

// Queries regroupe les accès données sur une connexion ou une transaction.
type Queries struct{ db DBTX }

func New(db DBTX) *Queries { return &Queries{db: db} }

// InTx exécute fn dans une transaction (commit si nil, rollback sinon).
func InTx(ctx context.Context, pool *pgxpool.Pool, fn func(q *Queries) error) error {
	return pgx.BeginFunc(ctx, pool, func(tx pgx.Tx) error { return fn(New(tx)) })
}

func notFound(err error) error {
	if errors.Is(err, pgx.ErrNoRows) {
		return ErrNotFound
	}
	return err
}

// IsUniqueViolation détecte une violation de contrainte d'unicité PostgreSQL.
func IsUniqueViolation(err error) bool {
	var pgErr *pgconn.PgError
	return errors.As(err, &pgErr) && pgErr.Code == "23505"
}
