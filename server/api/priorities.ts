import { accessCode, accessStatus, ownerId } from "../lib/auth.js";
import { database } from "../lib/db.js";

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const errors = {
  400: "Revisa las prioridades elegidas.",
  401: "Introduce tu código de acceso vigente.",
  409: "Las prioridades cambiaron. Actualiza antes de volver a guardar.",
  503: "La agenda no está disponible por ahora.",
};
function json(value: unknown, status = 200) { return Response.json(value, { status, headers: { "Cache-Control": "no-store" } }); }
function failure(status: keyof typeof errors) { return json({ error: errors[status] }, status); }
async function withOwner(request: Request, action: () => Promise<Response>) {
  if (!process.env.DATABASE_URL) return failure(503);
  const code = accessCode(request);
  if (!code) return failure(401);
  try { const status = await accessStatus(code); return status === 200 ? await action() : failure(status); }
  catch { return failure(503); }
}

export function GET(request: Request) {
  return withOwner(request, async () => {
    const { rows: [value] } = await database().query(`SELECT to_char(today.day, 'YYYY-MM-DD') AS day,
      COALESCE((SELECT version FROM agenda_priority_days WHERE owner_id=$1 AND day=today.day), 0) AS version,
      COALESCE((SELECT jsonb_agg(task_id ORDER BY position) FROM agenda_daily_priorities WHERE owner_id=$1 AND day=today.day), '[]'::jsonb) AS ids
      FROM (SELECT timezone('America/Santiago', now())::date AS day) today`, [ownerId]);
    return json(value);
  });
}

export function PUT(request: Request) {
  return withOwner(request, async () => {
    if (request.headers.get('content-type')?.split(';')[0]?.trim().toLowerCase() !== 'application/json') return failure(400);
    let input: Record<string, unknown>;
    try {
      const raw = await request.text();
      if (raw.length > 32_768) return failure(400);
      const parsed: unknown = JSON.parse(raw);
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return failure(400);
      input = parsed as Record<string, unknown>;
    } catch { return failure(400); }
    if (typeof input.day !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(input.day)
      || !Number.isSafeInteger(input.version) || (input.version as number) < 0
      || !Array.isArray(input.ids) || input.ids.length > 500
      || !input.ids.every((id: unknown) => typeof id === 'string' && uuid.test(id))) return failure(400);
    const ids = (input.ids as string[]).map(id => id.toLowerCase());
    if (new Set(ids).size !== ids.length) return failure(400);
    const client = await database().connect();
    try {
      await client.query('BEGIN');
      const { rows: [clock] } = await client.query(`SELECT to_char(timezone('America/Santiago', now())::date, 'YYYY-MM-DD') AS day`);
      if (input.day !== clock.day) { await client.query('ROLLBACK'); return failure(409); }
      await client.query('INSERT INTO agenda_priority_days(owner_id,day) VALUES ($1,$2::date) ON CONFLICT DO NOTHING', [ownerId, input.day]);
      const { rows: [state] } = await client.query('SELECT version FROM agenda_priority_days WHERE owner_id=$1 AND day=$2::date FOR UPDATE', [ownerId, input.day]);
      if (state.version !== input.version) {
        const { rows } = await client.query('SELECT task_id FROM agenda_daily_priorities WHERE owner_id=$1 AND day=$2::date ORDER BY position', [ownerId, input.day]);
        const same = state.version === (input.version as number) + 1 && rows.map(row => row.task_id).join(',') === ids.join(',');
        await client.query('ROLLBACK');
        return same ? json({ day: input.day, version: state.version, ids }) : failure(409);
      }
      const { rows: [eligible] } = await client.query(`SELECT count(*)::int AS count FROM agenda_tasks
        WHERE owner_id=$1 AND id=ANY($2::uuid[]) AND planned_on <= $3::date
          AND (completed_at IS NULL OR timezone('America/Santiago', completed_at)::date=$3::date)`, [ownerId, ids, input.day]);
      if (eligible.count !== ids.length) { await client.query('ROLLBACK'); return failure(409); }
      await client.query('DELETE FROM agenda_daily_priorities WHERE owner_id=$1 AND day=$2::date', [ownerId, input.day]);
      await client.query(`INSERT INTO agenda_daily_priorities(owner_id,day,task_id,position)
        SELECT $1,$2::date, task_id, (ordinality-1)::int FROM unnest($3::uuid[]) WITH ORDINALITY AS chosen(task_id,ordinality)`, [ownerId, input.day, ids]);
      const { rows: [next] } = await client.query('UPDATE agenda_priority_days SET version=version+1 WHERE owner_id=$1 AND day=$2::date RETURNING version', [ownerId, input.day]);
      await client.query('COMMIT');
      return json({ day: input.day, version: next.version, ids });
    } catch (error) { await client.query('ROLLBACK'); throw error; }
    finally { client.release(); }
  });
}
