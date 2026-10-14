// Package config charge la configuration depuis l'environnement.
package config

import (
	"errors"
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
)

const devJWTSecret = "dev-only-change-me-in-prod-min-32-bytes!!"

type Config struct {
	AppName            string
	Version            string
	Environment        string
	Port               string
	DatabaseURL        string
	JWTSecret          []byte
	JWTExpires         time.Duration
	CORSOrigins        []string
	MaxFindingsPerSync int
	// PublicURL sert à documenter la commande `claude mcp add` côté web.
	PublicURL string
}

func (c Config) IsDev() bool { return c.Environment == "dev" || c.Environment == "test" }

func env(key, def string) string {
	if v, ok := os.LookupEnv(key); ok && v != "" {
		return v
	}
	return def
}

func envInt(key string, def int) (int, error) {
	v := env(key, "")
	if v == "" {
		return def, nil
	}
	n, err := strconv.Atoi(v)
	if err != nil {
		return 0, fmt.Errorf("%s: %w", key, err)
	}
	return n, nil
}

func Load() (Config, error) {
	c := Config{
		AppName:     "CodeShield API",
		Version:     "2.0.0",
		Environment: env("ENVIRONMENT", "dev"),
		Port:        env("PORT", "8000"),
		DatabaseURL: env("DATABASE_URL", "postgres://codeshield:codeshield@localhost:5432/codeshield?sslmode=disable"),
		PublicURL:   strings.TrimRight(env("PUBLIC_URL", "http://localhost:8000"), "/"),
	}
	secret := env("JWT_SECRET_KEY", "")
	if secret == "" {
		if !c.IsDev() {
			return c, errors.New("JWT_SECRET_KEY est obligatoire hors environnement dev")
		}
		secret = devJWTSecret
	}
	if !c.IsDev() && (len(secret) < 32 || secret == devJWTSecret) {
		return c, errors.New("JWT_SECRET_KEY doit faire au moins 32 octets et ne pas être la valeur de dev")
	}
	c.JWTSecret = []byte(secret)

	mins, err := envInt("JWT_EXPIRES_MINUTES", 60*24)
	if err != nil {
		return c, err
	}
	c.JWTExpires = time.Duration(mins) * time.Minute

	if c.MaxFindingsPerSync, err = envInt("MAX_FINDINGS_PER_SYNC", 5000); err != nil {
		return c, err
	}

	origins := env("CORS_ORIGINS", "http://localhost:3000,http://localhost:5173")
	for _, o := range strings.Split(origins, ",") {
		if o = strings.TrimSpace(o); o != "" {
			c.CORSOrigins = append(c.CORSOrigins, o)
		}
	}
	return c, nil
}
