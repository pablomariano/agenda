CREATE TABLE IF NOT EXISTS agenda_ai_recoveries (
    owner_id text NOT NULL,
    id uuid NOT NULL,
    provider text NOT NULL CHECK (provider IN ('codex', 'gemini')),
    window_kind text NOT NULL CHECK (window_kind IN ('short', 'weekly')),
    expected_at timestamptz NOT NULL,
    decision text NOT NULL CHECK (decision IN ('wait', 'continue')),
    alternative text CHECK (alternative IN ('codex', 'gemini')),
    task_id uuid,
    cancelled boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    version integer NOT NULL DEFAULT 1 CHECK (version > 0),
    PRIMARY KEY (owner_id, id),
    FOREIGN KEY (owner_id, task_id) REFERENCES agenda_tasks (owner_id, id),
    CHECK ((decision = 'wait' AND alternative IS NULL) OR
           (decision = 'continue' AND alternative IS NOT NULL AND alternative <> provider))
);
