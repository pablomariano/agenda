BEGIN;
ALTER TABLE agenda_projects ADD COLUMN IF NOT EXISTS description text NOT NULL DEFAULT '';
ALTER TABLE agenda_tasks ADD COLUMN IF NOT EXISTS description text NOT NULL DEFAULT '';
CREATE TABLE IF NOT EXISTS agenda_ideas (
    owner_id text NOT NULL,
    id uuid NOT NULL,
    content text NOT NULL CHECK (char_length(btrim(content)) > 0 AND char_length(content) <= 4000),
    created_at timestamptz NOT NULL DEFAULT now(),
    converted_project_id uuid,
    converted_task_id uuid,
    conversion_request jsonb,
    PRIMARY KEY (owner_id, id),
    FOREIGN KEY (owner_id, converted_project_id) REFERENCES agenda_projects (owner_id, id),
    FOREIGN KEY (owner_id, converted_task_id) REFERENCES agenda_tasks (owner_id, id),
    CHECK ((converted_project_id IS NULL AND converted_task_id IS NULL AND conversion_request IS NULL)
        OR (num_nonnulls(converted_project_id, converted_task_id) = 1 AND conversion_request IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS agenda_ideas_pending_idx ON agenda_ideas(owner_id, created_at, id)
  WHERE conversion_request IS NULL;
COMMIT;
