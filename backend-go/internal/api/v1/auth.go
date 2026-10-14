package v1

import (
	"errors"
	"net/http"
	"net/mail"
	"strings"
	"unicode/utf8"

	"codeshield/backend/internal/domain"
	"codeshield/backend/internal/repository"
	"codeshield/backend/internal/service"
)

type credentials struct {
	Email    string `json:"email"`
	Password string `json:"password"`
}

func validEmail(e string) bool {
	addr, err := mail.ParseAddress(e)
	if err != nil || addr.Address != e || len(e) > 320 {
		return false
	}
	_, domainPart, _ := strings.Cut(e, "@")
	return strings.Contains(domainPart, ".")
}

func (c *credentials) validate(minPassword int) []domain.FieldError {
	var errs []domain.FieldError
	c.Email = strings.TrimSpace(c.Email)
	if !validEmail(c.Email) {
		errs = append(errs, domain.FieldError{Loc: []any{"body", "email"}, Msg: "value is not a valid email address"})
	}
	if n := utf8.RuneCountInString(c.Password); n < minPassword || n > 128 {
		errs = append(errs, domain.FieldError{Loc: []any{"body", "password"}, Msg: "invalid password length"})
	}
	return errs
}

func (a *API) register(w http.ResponseWriter, r *http.Request) {
	var body credentials
	if !decodeJSON(w, r, &body, false) {
		return
	}
	if errs := body.validate(8); len(errs) > 0 {
		writeValidation(w, errs)
		return
	}
	if _, err := a.Q.UserByEmail(r.Context(), body.Email); err == nil {
		writeError(w, http.StatusBadRequest, "Email already registered")
		return
	} else if !errors.Is(err, repository.ErrNotFound) {
		internalError(w, r, err)
		return
	}
	hash, err := service.HashPassword(body.Password)
	if err != nil {
		internalError(w, r, err)
		return
	}
	u, err := a.Q.CreateUser(r.Context(), body.Email, hash)
	if repository.IsUniqueViolation(err) {
		writeError(w, http.StatusBadRequest, "Email already registered")
		return
	}
	if err != nil {
		internalError(w, r, err)
		return
	}
	writeJSON(w, http.StatusCreated, u)
}

func (a *API) login(w http.ResponseWriter, r *http.Request) {
	var body credentials
	if !decodeJSON(w, r, &body, false) {
		return
	}
	if errs := body.validate(1); len(errs) > 0 {
		writeValidation(w, errs)
		return
	}
	u, err := a.Q.UserByEmail(r.Context(), body.Email)
	if err != nil {
		if !errors.Is(err, repository.ErrNotFound) {
			internalError(w, r, err)
			return
		}
		service.BurnPasswordCheck(body.Password)
		writeError(w, http.StatusUnauthorized, "Invalid credentials")
		return
	}
	if !service.VerifyPassword(body.Password, u.PasswordHash) {
		writeError(w, http.StatusUnauthorized, "Invalid credentials")
		return
	}
	tok, err := a.Tokens.Issue(u.ID)
	if err != nil {
		internalError(w, r, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"access_token": tok, "token_type": "bearer"})
}

func (a *API) me(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, principalFrom(r.Context()).User)
}
