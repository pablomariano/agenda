CREATE TABLE IF NOT EXISTS agenda_priority_days (
    owner_id text NOT NULL,
    day date NOT NULL,
    version integer NOT NULL DEFAULT 0 CHECK (version >= 0),
    PRIMARY KEY (owner_id, day)
);

CREATE TABLE IF NOT EXISTS agenda_daily_priorities (
    owner_id text NOT NULL,
    day date NOT NULL,
    task_id uuid NOT NULL,
    position integer NOT NULL CHECK (position >= 0),
    PRIMARY KEY (owner_id, day, task_id),
    UNIQUE (owner_id, day, position),
    FOREIGN KEY (owner_id, day) REFERENCES agenda_priority_days (owner_id, day),
    FOREIGN KEY (owner_id, task_id) REFERENCES agenda_tasks (owner_id, id)
);
