BEGIN;

CREATE TABLE IF NOT EXISTS agenda_access (
    owner_id text PRIMARY KEY CHECK (owner_id = 'personal'),
    token_sha256 bytea NOT NULL CHECK (octet_length(token_sha256) = 32)
);

CREATE TABLE IF NOT EXISTS agenda_projects (
    owner_id text NOT NULL,
    id uuid NOT NULL,
    title text NOT NULL CHECK (char_length(btrim(title)) BETWEEN 1 AND 120),
    importance text NOT NULL CHECK (importance IN ('alta', 'media', 'baja')),
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (owner_id, id)
);

CREATE TABLE IF NOT EXISTS agenda_tasks (
    owner_id text NOT NULL,
    id uuid NOT NULL,
    project_id uuid NOT NULL,
    title text NOT NULL CHECK (char_length(btrim(title)) BETWEEN 1 AND 120),
    impact text NOT NULL CHECK (impact IN ('alta', 'media', 'baja')),
    planned_on date NOT NULL DEFAULT (timezone('America/Santiago', now())::date),
    completed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (owner_id, id),
    FOREIGN KEY (owner_id, project_id) REFERENCES agenda_projects (owner_id, id)
);

COMMIT;
