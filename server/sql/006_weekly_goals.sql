BEGIN;

CREATE TABLE IF NOT EXISTS agenda_weekly_goals (
    owner_id text NOT NULL,
    id uuid NOT NULL,
    week_start date NOT NULL CHECK (extract(isodow FROM week_start) = 1),
    title text NOT NULL CHECK (char_length(btrim(title)) BETWEEN 1 AND 120),
    version integer NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (owner_id, id)
);

CREATE INDEX IF NOT EXISTS agenda_weekly_goals_week_idx ON agenda_weekly_goals (owner_id, week_start, created_at, id);
COMMIT;
