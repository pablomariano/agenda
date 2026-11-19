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
          'plannedOn', t.planned_on, 'carriedOver', t.planned_on < today.date)
          ORDER BY array_position(ARRAY['alta','media','baja'], p.importance),
            array_position(ARRAY['alta','media','baja'], t.impact), t.created_at, t.id)
          FROM agenda_tasks t
          JOIN agenda_projects p ON p.owner_id = t.owner_id AND p.id = t.project_id
          WHERE t.owner_id = $1 AND t.planned_on <= today.date AND t.completed_at IS NULL),
          '[]'::jsonb) AS tasks
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
    const { rowCount } = await database().query(`
      INSERT INTO agenda_tasks (owner_id, id, title, impact, project_id)
      SELECT $1, $2, $3, $4, id FROM agenda_projects WHERE owner_id = $1 AND id = $5
      ON CONFLICT (owner_id, id) DO UPDATE SET title = excluded.title
      WHERE agenda_tasks.title = excluded.title AND agenda_tasks.impact = excluded.impact
        AND agenda_tasks.project_id = excluded.project_id
      RETURNING id
    `, [owner, id, title, level, projectId]);
    return rowCount ? json({ id }, 201) : failure(409);
  });
}
