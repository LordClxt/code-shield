package service

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"strconv"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"golang.org/x/crypto/bcrypt"
)

const APIKeyPrefix = "csk_"

// bcrypt est limité à 72 octets : on tronque explicitement (parité v1).
func truncate72(p string) []byte {
	b := []byte(p)
	if len(b) > 72 {
		b = b[:72]
	}
	return b
}

func HashPassword(plain string) (string, error) {
	h, err := bcrypt.GenerateFromPassword(truncate72(plain), bcrypt.DefaultCost)
	return string(h), err
}

func VerifyPassword(plain, hash string) bool {
	return bcrypt.CompareHashAndPassword([]byte(hash), truncate72(plain)) == nil
}

// dummyHash : comparaison à coût constant quand l'email est inconnu (anti-énumération).
var dummyHash, _ = bcrypt.GenerateFromPassword([]byte("codeshield-timing-equalizer"), bcrypt.DefaultCost)

func BurnPasswordCheck(plain string) {
	_ = bcrypt.CompareHashAndPassword(dummyHash, truncate72(plain))
}

type Tokens struct {
	Secret  []byte
	Expires time.Duration
}

func (t Tokens) Issue(userID int64) (string, error) {
	now := time.Now()
	claims := jwt.RegisteredClaims{
		Subject:   strconv.FormatInt(userID, 10),
		IssuedAt:  jwt.NewNumericDate(now),
		ExpiresAt: jwt.NewNumericDate(now.Add(t.Expires)),
	}
	return jwt.NewWithClaims(jwt.SigningMethodHS256, claims).SignedString(t.Secret)
}

// Parse retourne l'id utilisateur, ou une erreur si le token est invalide/expiré.
func (t Tokens) Parse(token string) (int64, error) {
	var claims jwt.RegisteredClaims
	_, err := jwt.ParseWithClaims(token, &claims, func(*jwt.Token) (any, error) { return t.Secret, nil },
		jwt.WithValidMethods([]string{"HS256"}), jwt.WithExpirationRequired())
	if err != nil {
		return 0, err
	}
	id, err := strconv.ParseInt(claims.Subject, 10, 64)
	if err != nil || id <= 0 {
		return 0, errors.New("invalid subject")
	}
	return id, nil
}

// GenerateAPIKey retourne (clé en clair, hash sha256, préfixe affichable).
// Seul le hash est stocké ; la clé en clair n'est montrée qu'une fois.
func GenerateAPIKey() (plain, hash, prefix string, err error) {
	raw := make([]byte, 32)
	if _, err = rand.Read(raw); err != nil {
		return
	}
	plain = APIKeyPrefix + base64.RawURLEncoding.EncodeToString(raw)
	return plain, HashAPIKey(plain), plain[:12], nil
}

func HashAPIKey(plain string) string {
	sum := sha256.Sum256([]byte(plain))
	return hex.EncodeToString(sum[:])
}
