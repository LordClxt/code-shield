.PHONY: run test lint contracts docker

run:
	go run ./cmd/api

# PostgreSQL embarqué par défaut ; TEST_DATABASE_URL pour une base existante.
test: contracts
	go test ./...

lint:
	gofmt -l . | (! grep .) && go vet ./...

# contracts/openapi.yaml est la source de vérité ; le binaire en embarque une copie.
contracts:
	cp ../contracts/openapi.yaml internal/api/openapi.yaml

docker:
	docker compose up --build
