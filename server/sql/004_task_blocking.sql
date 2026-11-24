ALTER TABLE agenda_tasks ADD COLUMN IF NOT EXISTS blocked_by text
  CHECK (blocked_by IN ('codex', 'gemini'));
