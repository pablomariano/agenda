import { accessCode, accessStatus, ownerId } from "../lib/auth.js";
import { database } from "../lib/db.js";

const levels = ["alta", "media", "baja"];
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const errors = {
  400: "Revisa el título, el nivel y el proyecto.",
  401: "Introduce tu código de acceso vigente.",
  409: "No se pudo confirmar el guardado. Actualiza la agenda antes de volver a intentarlo.",
  503: "La agenda no está disponible por ahora.",
};

function validDate(value: unknown): value is string {
  if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const parsed = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value && value >= "0001-01-01";
}

function validTime(value: unknown): value is string | null {
  return value === null || (typeof value === "string" && /^(?:[01]\d|2[0-3]):[0-5]\d$/.test(value));
}

function json(body: unknown, status = 200) {
  return Response.json(body, { status, headers: { "Cache-Control": "no-store" } });
}

function failure(status: keyof typeof errors) {
  return json({ error: errors[status] }, status);
}

async function withOwner(request: Request, action: (owner: string) => Promise<Response>) {
  if (!process.env.DATABASE_URL) return failure(503);
  const code = accessCode(request);
  if (!code) return failure(401);
  try {
    const status = await accessStatus(code);
    return status === 200 ? await action(ownerId) : failure(status);
  } catch {
    return failure(503);
  }
}

export function GET(request: Request) {
  return withOwner(request, async owner => {
    const { rows: [agenda] } = await database().query(`
      SELECT
        COALESCE((SELECT jsonb_agg(jsonb_build_object('id', id, 'title', title,
          'importance', importance) ORDER BY created_at, id)
          FROM agenda_projects WHERE owner_id = $1), '[]'::jsonb) AS projects,
        COALESCE((SELECT jsonb_agg(jsonb_build_object('id', t.id, 'title', t.title,
          'projectId', p.id, 'projectTitle', p.title, 'impact', t.impact,
          'plannedOn', t.planned_on, 'plannedTime', to_char(t.planned_time, 'HH24:MI'), 'carriedOver', t.planned_on < today.date)
          ORDER BY array_position(ARRAY['alta','media','baja'], p.importance),
            array_position(ARRAY['alta','media','baja'], t.impact), t.created_at, t.id)
          FROM agenda_tasks t
          JOIN agenda_projects p ON p.owner_id = t.owner_id AND p.id = t.project_id
          WHERE t.owner_id = $1 AND t.planned_on <= today.date AND t.completed_at IS NULL),
          '[]'::jsonb) AS tasks
        , COALESCE((SELECT jsonb_agg(jsonb_build_object('id', t.id, 'title', t.title,
          'projectId', p.id, 'projectTitle', p.title, 'impact', t.impact,
          'plannedOn', t.planned_on, 'plannedTime', to_char(t.planned_time, 'HH24:MI'), 'carriedOver', false)
          ORDER BY t.planned_on, t.planned_time NULLS LAST, t.created_at, t.id)
          FROM agenda_tasks t JOIN agenda_projects p ON p.owner_id = t.owner_id AND p.id = t.project_id
          WHERE t.owner_id = $1 AND t.planned_on > today.date AND t.completed_at IS NULL), '[]'::jsonb) AS "futureTasks"
        , COALESCE((SELECT jsonb_agg(jsonb_build_object('id', t.id, 'title', t.title,
          'projectId', p.id, 'projectTitle', p.title, 'impact', t.impact,
          'plannedOn', t.planned_on, 'plannedTime', to_char(t.planned_time, 'HH24:MI'), 'carriedOver', false, 'completed', true,
          'completedToday', timezone('America/Santiago', t.completed_at)::date = today.date)
          ORDER BY t.completed_at DESC, t.id)
          FROM agenda_tasks t
          JOIN agenda_projects p ON p.owner_id = t.owner_id AND p.id = t.project_id
          WHERE t.owner_id = $1 AND t.completed_at IS NOT NULL), '[]'::jsonb) AS "completedTasks"
      FROM (SELECT timezone('America/Santiago', now())::date AS date) today
    `, [owner]);
    return json(agenda);
  });
}

export function POST(request: Request) {
  return withOwner(request, async owner => {
    if (request.headers.get("content-type")?.split(";")[0]?.trim().toLowerCase() !== "application/json") {
      return failure(400);
    }
    let body: Record<string, unknown>;
    try {
      const raw = await request.text();
      if (raw.length > 8_192) return failure(400);
      const parsed: unknown = JSON.parse(raw);
      if (typeof parsed !== "object" || !parsed || Array.isArray(parsed)) return failure(400);
      body = parsed as Record<string, unknown>;
    } catch {
      return failure(400);
    }
    const { kind, id, level, projectId } = body;
    const title = typeof body.title === "string" ? body.title.trim() : "";
    if (typeof id !== "string" || !uuid.test(id) || !title || [...title].length > 120
      || title.includes("\u0000") || typeof level !== "string" || !levels.includes(level)) {
      return failure(400);
    }

    if (kind === "project") {
      const { rowCount } = await database().query(`
        INSERT INTO agenda_projects (owner_id, id, title, importance)
        VALUES ($1, $2, $3, $4)
        ON CONFLICT (owner_id, id) DO UPDATE SET title = excluded.title
        WHERE agenda_projects.title = excluded.title AND agenda_projects.importance = excluded.importance
        RETURNING id
      `, [owner, id, title, level]);
      return rowCount ? json({ id }, 201) : failure(409);
    }

    if (kind !== "task" || typeof projectId !== "string" || !uuid.test(projectId)) {
      return failure(400);
    }
    const hasPlan = body.plannedOn !== undefined || body.plannedTime !== undefined;
    if (hasPlan && (!validDate(body.plannedOn) || !validTime(body.plannedTime))) return failure(400);
    const { rowCount } = await database().query(`
      INSERT INTO agenda_tasks (owner_id, id, title, impact, project_id, planned_on, planned_time)
      SELECT $1, $2, $3, $4, id, COALESCE($6::date, timezone('America/Santiago', now())::date), $7::time FROM agenda_projects WHERE owner_id = $1 AND id = $5
      ON CONFLICT (owner_id, id) DO UPDATE SET title = excluded.title
      WHERE agenda_tasks.title = excluded.title AND agenda_tasks.impact = excluded.impact
        AND agenda_tasks.project_id = excluded.project_id
        AND (NOT $8::boolean OR (agenda_tasks.planned_on = excluded.planned_on AND agenda_tasks.planned_time IS NOT DISTINCT FROM excluded.planned_time))
      RETURNING id
    `, [owner, id, title, level, projectId, hasPlan ? body.plannedOn : null, hasPlan ? body.plannedTime : null, hasPlan]);
    return rowCount ? json({ id }, 201) : failure(409);
  });
}

export function PUT(request: Request) {
  return withOwner(request, async owner => {
    if (request.headers.get("content-type")?.split(";")[0]?.trim().toLowerCase() !== "application/json") return failure(400);
    let body: Record<string, unknown>;
    try {
      const raw = await request.text();
      if (raw.length > 8_192) return failure(400);
      const parsed: unknown = JSON.parse(raw);
      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return failure(400);
      body = parsed as Record<string, unknown>;
    } catch { return failure(400); }

    const valid = (item: unknown): item is { title: string; projectId: string; impact: string; completed: boolean } => {
      if (!item || typeof item !== "object" || Array.isArray(item)) return false;
      const value = item as Record<string, unknown>;
      return typeof value.title === "string" && value.title === value.title.trim()
        && value.title.length > 0 && [...value.title].length <= 120 && !value.title.includes("\u0000")
        && typeof value.projectId === "string" && uuid.test(value.projectId)
        && typeof value.impact === "string" && levels.includes(value.impact)
        && typeof value.completed === "boolean";
    };
    if (typeof body.id !== "string" || !uuid.test(body.id) || !valid(body.expected) || !valid(body.task)) return failure(400);
    const before = body.expected, next = body.task;
    const beforePlan = body.expected as typeof before & { plannedOn?: unknown; plannedTime?: unknown };
    const nextPlan = body.task as typeof next & { plannedOn?: unknown; plannedTime?: unknown };
    const hasPlan = beforePlan.plannedOn !== undefined || beforePlan.plannedTime !== undefined || nextPlan.plannedOn !== undefined || nextPlan.plannedTime !== undefined;
    if (hasPlan && (!validDate(beforePlan.plannedOn) || !validTime(beforePlan.plannedTime) || !validDate(nextPlan.plannedOn) || !validTime(nextPlan.plannedTime))) return failure(400);
    const { rowCount } = await database().query(`
      UPDATE agenda_tasks t SET title = $3, project_id = $4, impact = $5,
        completed_at = CASE WHEN $6::boolean THEN COALESCE(t.completed_at, now()) ELSE NULL END,
        planned_on = CASE WHEN $11::boolean THEN $12::date ELSE t.planned_on END,
        planned_time = CASE WHEN $11::boolean THEN $13::time ELSE t.planned_time END
      WHERE t.owner_id = $1 AND t.id = $2
        AND EXISTS (SELECT 1 FROM agenda_projects p WHERE p.owner_id = $1 AND p.id = $4)
        AND ((t.title = $7 AND t.project_id = $8 AND t.impact = $9 AND (t.completed_at IS NOT NULL) = $10::boolean AND (NOT $11::boolean OR (t.planned_on = $14::date AND t.planned_time IS NOT DISTINCT FROM $15::time)))
          OR (t.title = $3 AND t.project_id = $4 AND t.impact = $5 AND (t.completed_at IS NOT NULL) = $6::boolean AND (NOT $11::boolean OR (t.planned_on = $12::date AND t.planned_time IS NOT DISTINCT FROM $13::time))))
      RETURNING t.id
    `, [owner, body.id, next.title, next.projectId, next.impact, next.completed,
      before.title, before.projectId, before.impact, before.completed, hasPlan, nextPlan.plannedOn ?? null, nextPlan.plannedTime ?? null, beforePlan.plannedOn ?? null, beforePlan.plannedTime ?? null]);
    return rowCount ? json({ id: body.id }) : failure(409);
  });
}
