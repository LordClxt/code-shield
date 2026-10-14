-- +goose Up
-- Schéma CodeShield v2. AUCUNE colonne ne contient de code source :
-- uniquement des métadonnées de positionnement et des empreintes (sha256).

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE projects (
    id         BIGSERIAL PRIMARY KEY,
    owner_id   BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(200) NOT NULL,
    repo_url   VARCHAR(500),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_projects_owner ON projects(owner_id);

CREATE TABLE scans (
    id             BIGSERIAL PRIMARY KEY,
    project_id     BIGINT      NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    findings_count INTEGER     NOT NULL DEFAULT 0,
    commit_sha     VARCHAR(64),
    branch         VARCHAR(255),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_scans_project ON scans(project_id, id DESC);

CREATE TABLE api_keys (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name         VARCHAR(200) NOT NULL,
    key_hash     CHAR(64)     NOT NULL UNIQUE,
    prefix       VARCHAR(16)  NOT NULL,
    scopes       TEXT[]       NOT NULL DEFAULT '{sync:write}',
    project_id   BIGINT       REFERENCES projects(id) ON DELETE CASCADE,
    revoked      BOOLEAN      NOT NULL DEFAULT false,
    last_used_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_api_keys_user ON api_keys(user_id);

CREATE TABLE vulnerabilities (
    id                BIGSERIAL PRIMARY KEY,
    project_id        BIGINT        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    last_scan_id      BIGINT        REFERENCES scans(id) ON DELETE SET NULL,
    tool              VARCHAR(32)   NOT NULL,
    rule_id           VARCHAR(320)  NOT NULL,
    file_path         VARCHAR(1024) NOT NULL,
    line_number       INTEGER       NOT NULL,
    end_line          INTEGER,
    start_column      INTEGER,
    end_column        INTEGER,
    severity          VARCHAR(16)   NOT NULL,
    title             VARCHAR(500)  NOT NULL,
    message           TEXT,
    category          VARCHAR(16)   NOT NULL,
    cwe               TEXT[]        NOT NULL DEFAULT '{}',
    owasp             TEXT[]        NOT NULL DEFAULT '{}',
    remediation_key   VARCHAR(200),
    package_name      VARCHAR(500),
    installed_version VARCHAR(200),
    fixed_version     VARCHAR(200),
    fingerprint       CHAR(64),
    status            VARCHAR(24)   NOT NULL DEFAULT 'OPEN',
    first_seen        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    last_seen         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    resolved_at       TIMESTAMPTZ,
    resolved_by       VARCHAR(64),
    resolution_note   TEXT
);
CREATE INDEX ix_vulns_project_status ON vulnerabilities(project_id, status);
-- Identité stable (survit aux décalages de lignes) quand le desktop fournit une empreinte.
CREATE UNIQUE INDEX uq_vuln_fingerprint ON vulnerabilities(project_id, fingerprint)
    WHERE fingerprint IS NOT NULL;
-- Identité v1 (outil, règle, fichier, ligne) pour les findings sans empreinte.
CREATE UNIQUE INDEX uq_vuln_identity ON vulnerabilities(project_id, tool, rule_id, file_path, line_number)
    WHERE fingerprint IS NULL;

-- +goose Down
DROP TABLE vulnerabilities;
DROP TABLE api_keys;
DROP TABLE scans;
DROP TABLE projects;
DROP TABLE users;
