// Command devdb démarre un PostgreSQL embarqué pour le développement local
// (aucun Docker requis) et affiche le DATABASE_URL à utiliser :
//
//	go run ./cmd/devdb            # Ctrl+C pour arrêter
//	DATABASE_URL=... go run ./cmd/api
//
// Les données persistent dans ~/.codeshield/devdb.
package main

import (
	"fmt"
	"log"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"

	embeddedpostgres "github.com/fergusstrange/embedded-postgres"
)

func main() {
	home, err := os.UserHomeDir()
	if err != nil {
		log.Fatal(err)
	}
	base := filepath.Join(home, ".codeshield", "devdb")
	port := uint32(55432)
	pg := embeddedpostgres.NewDatabase(embeddedpostgres.DefaultConfig().
		Port(port).Database("codeshield").Username("codeshield").Password("codeshield").
		DataPath(filepath.Join(base, "data")).
		RuntimePath(filepath.Join(base, "runtime")).
		CachePath(filepath.Join(base, "cache")))
	if err := pg.Start(); err != nil {
		log.Fatal(err)
	}
	fmt.Printf("DATABASE_URL=postgres://codeshield:codeshield@127.0.0.1:%d/codeshield?sslmode=disable\n", port)

	sig := make(chan os.Signal, 1)
	signal.Notify(sig, os.Interrupt, syscall.SIGTERM)
	<-sig
	// Le serveur est déjà arrêté par pg_ctl ; une erreur ici est sans conséquence.
	if err := pg.Stop(); err != nil {
		log.Printf("arrêt : %v", err)
	}
}
