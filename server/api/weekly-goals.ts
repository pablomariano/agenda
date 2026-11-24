import { accessCode, accessStatus, ownerId } from "../lib/auth.js";
import { database } from "../lib/db.js";

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const errors = { 400: "Revisa los datos del objetivo.", 401: "Introduce tu código de acceso vigente.", 409: "El objetivo cambió. Actualiza e inténtalo de nuevo.", 503: "La agenda no está disponible por ahora." };
function json(value: unknown, status = 200) { return Response.json(value, { status, headers: { "Cache-Control": "no-store" } }); }
function failure(status: keyof typeof errors) { return json({ error: errors[status] }, status); }
async function withOwner(request: Request, action: () => Promise<Response>) {
  if (!process.env.DATABASE_URL) return failure(503);
  const code = accessCode(request);
  if (!code) return failure(401);
  try { const status = await accessStatus(code); return status === 200 ? await action() : failure(status); }
  catch { return failure(503); }
}
function validWeek(value: unknown): value is string {
  if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(value) || value < "0001-01-01") return false;
  const date = new Date(value + "T00:00:00Z");
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value && date.getUTCDay() === 1;
}
async function body(request: Request): Promise<Record<string, unknown> | null> {
  if (request.headers.get("content-type")?.split(";")[0]?.trim().toLowerCase() !== "application/json") return null;
  try {
    const raw = await request.text();
    if (raw.length > 8192) return null;
    const value: unknown = JSON.parse(raw);
    return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : null;
  } catch { return null; }
}
export function GET(request: Request) {
  return withOwner(request, async () => {
    const week = new URL(request.url).searchParams.get("week");
    if (!validWeek(week)) return failure(400);
    const { rows } = await database().query(`SELECT id, to_char(week_start, 'YYYY-MM-DD') AS "weekStart", title, version
      FROM agenda_weekly_goals WHERE owner_id=$1 AND week_start=$2::date ORDER BY created_at, id`, [ownerId, week]);
    return json({ goals: rows });
  });
}
export function POST(request: Request) {
  return withOwner(request, async () => {
    const input = await body(request);
    if (!input || typeof input.id !== "string" || !uuid.test(input.id) || !validWeek(input.weekStart)
      || typeof input.title !== "string" || !input.title.trim() || Array.from(input.title.trim()).length > 120) return failure(400);
    const { rowCount } = await database().query(`INSERT INTO agenda_weekly_goals (owner_id,id,week_start,title)
      VALUES ($1,$2,$3::date,$4) ON CONFLICT (owner_id,id) DO UPDATE SET id=excluded.id
      WHERE agenda_weekly_goals.week_start=excluded.week_start AND agenda_weekly_goals.title=excluded.title AND agenda_weekly_goals.version=1 RETURNING id`,
      [ownerId, input.id, input.weekStart, input.title.trim()]);
    return rowCount ? json({ id: input.id, version: 1 }, 201) : failure(409);
  });
}
export function PUT(request: Request) {
  return withOwner(request, async () => {
    const input = await body(request);
    if (!input || typeof input.id !== "string" || !uuid.test(input.id) || !Number.isSafeInteger(input.version)
      || (input.version as number) < 1 || typeof input.title !== "string" || !input.title.trim()
      || Array.from(input.title.trim()).length > 120) return failure(400);
    const { rowCount } = await database().query(`UPDATE agenda_weekly_goals SET title=$3,
      version=CASE WHEN version=$4 THEN version+1 ELSE version END
      WHERE owner_id=$1 AND id=$2 AND (version=$4 OR (version=$4+1 AND title=$3)) RETURNING id`,
      [ownerId, input.id, input.title.trim(), input.version]);
    return rowCount ? json({ id: input.id, version: (input.version as number) + 1 }) : failure(409);
  });
}
