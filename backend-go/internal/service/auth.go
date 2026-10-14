package service

import (
	"context"
	"errors"
	"fmt"
	"slices"
	"strings"

	"codeshield/backend/internal/domain"
	"codeshield/backend/internal/repository"
)

var ErrUnauthorized = errors.New("invalid credentials")

// Principal : identité authentifiée (utilisateur via JWT, ou clé API).
type Principal struct {
	User domain.User
	Key  *domain.APIKey // nil = session JWT (tous les droits de l'utilisateur)
}

func (p Principal) Has(s domain.Scope) bool {
	return p.Key == nil || slices.Contains(p.Key.Scopes, s)
}

// ProjectRestriction : projet auquel une clé est liée, nil sinon.
func (p Principal) ProjectRestriction() *int64 {
	if p.Key == nil {
		return nil
	}
	return p.Key.ProjectID
}

// CanAccessProject vérifie la restriction de projet d'une clé.
func (p Principal) CanAccessProject(id int64) bool {
	r := p.ProjectRestriction()
	return r == nil || *r == id
}

// Actor : identifiant court tracé dans resolved_by.
func (p Principal) Actor() string {
	if p.Key != nil {
		return "key:" + p.Key.Prefix
	}
	return fmt.Sprintf("user:%d", p.User.ID)
}

type Authenticator struct {
	Q      *repository.Queries
	Tokens Tokens
}

// Authenticate accepte un JWT utilisateur, ou une clé `csk_` si allowKey.
func (a Authenticator) Authenticate(ctx context.Context, token string, allowKey bool) (Principal, error) {
	if token == "" {
		return Principal{}, ErrUnauthorized
	}
	if strings.HasPrefix(token, APIKeyPrefix) {
		if !allowKey {
			return Principal{}, ErrUnauthorized
		}
		k, err := a.Q.APIKeyByHash(ctx, HashAPIKey(token))
		if err != nil || k.Revoked {
			return Principal{}, ErrUnauthorized
		}
		u, err := a.Q.UserByID(ctx, k.UserID)
		if err != nil {
			return Principal{}, ErrUnauthorized
		}
		_ = a.Q.TouchAPIKey(ctx, k.ID)
		return Principal{User: u, Key: &k}, nil
	}
	id, err := a.Tokens.Parse(token)
	if err != nil {
		return Principal{}, ErrUnauthorized
	}
	u, err := a.Q.UserByID(ctx, id)
	if err != nil {
		return Principal{}, ErrUnauthorized
	}
	return Principal{User: u}, nil
}
