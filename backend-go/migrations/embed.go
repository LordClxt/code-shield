// Package migrations embarque les migrations goose dans le binaire.
package migrations

import "embed"

//go:embed *.sql
var FS embed.FS
