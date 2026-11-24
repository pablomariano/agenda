import { accessCode, accessStatus, ownerId } from "../lib/auth.js";
import { database } from "../lib/db.js";

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const providers = ["codex", "gemini"];
const isoInstant = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/;
const errors = {
  400: "Revisa los datos de la recuperación.",
  401: "Introduce tu código de acceso vigente.",
  409: "La recuperación cambió o no se pudo guardar. Actualiza e inténtalo de nuevo.",
  503: "La agenda no está disponible por ahora.",
};
function json(body: unknown, status = 200) {
  return Response.json(body, { status, headers: { "Cache-Control": "no-store" } });
}
function failure(status: keyof typeof errors) { return json({ error: errors[status] }, status); }
async function withOwner(request: Request, action: () => Promise<Response>) {
  if (!process.env.DATABASE_URL) return failure(503);
  const code = accessCode(request);
  if (!code) return failure(401);
  try {
    const status = await accessStatus(code);
    return status === 200 ? await action() : failure(status);
  } catch { return failure(503); }
}
type Recovery = { provider: string; window: string; expectedAt: string; decision: string; alternative: string | null; taskId: string | null; cancelled: boolean };
function valid(value: unknown): value is Recovery {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const item = value as Record<string, unknown>;
  if (typeof item.provider !== "string" || !providers.includes(item.provider)
    || (item.window !== "short" && item.window !== "weekly")
    || typeof item.expectedAt !== "string" || !isoInstant.test(item.expectedAt)
    || item.expectedAt < "0001-01-01T00:00:00.000Z"
    || !Number.isFinite(Date.parse(item.expectedAt)) || new Date(item.expectedAt).toISOString() !== item.expectedAt
    || typeof item.cancelled !== "boolean") return false;
  if (item.taskId !== null && (typeof item.taskId !== "string" || !uuid.test(item.taskId))) return false;
  return (item.decision === "wait" && item.alternative === null)
    || (item.decision === "continue" && typeof item.alternative === "string"
      && providers.includes(item.alternative) && item.alternative !== item.provider);
}
async function body(request: Request): Promise<Record<string, unknown> | null> {
  if (request.headers.get("content-type")?.split(";")[0]?.trim().toLowerCase() !== "application/json") return null;
  try {
    const raw = await request.text();
    if (raw.length > 8192) return null;
    const parsed: unknown = JSON.parse(raw);
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed as Record<string, unknown> : null;
  } catch { return null; }
}
export function GET(request: Request) {
  return withOwner(request, async () => {
    const { rows } = await database().query(`SELECT id, provider, window_kind AS "window", expected_at AS "expectedAt", decision,
      alternative, task_id AS "taskId", cancelled, created_at AS "createdAt", version
      FROM agenda_ai_recoveries WHERE owner_id = $1 ORDER BY expected_at, id`, [ownerId]);
    return json({ recoveries: rows });
  });
}
export function POST(request: Request) {
  return withOwner(request, async () => {
    const input = await body(request);
    if (!input || typeof input.id !== "string" || !uuid.test(input.id) || !valid(input.recovery)) return failure(400);
    const item = input.recovery;
    if (item.cancelled) return failure(400);
    const future = Date.parse(item.expectedAt) > Date.now();
    if (!future) {
      const existing = await database().query(`SELECT 1 FROM agenda_ai_recoveries WHERE owner_id=$1 AND id=$2
        AND provider=$3 AND window_kind=$4 AND expected_at=$5::timestamptz AND decision=$6
        AND alternative IS NOT DISTINCT FROM $7 AND task_id IS NOT DISTINCT FROM $8::uuid AND NOT cancelled`,
        [ownerId, input.id, item.provider, item.window, item.expectedAt, item.decision, item.alternative, item.taskId]);
      return existing.rowCount ? json({ id: input.id }, 201) : failure(400);
    }
    const { rowCount } = await database().query(`INSERT INTO agenda_ai_recoveries
      (owner_id, id, provider, window_kind, expected_at, decision, alternative, task_id, cancelled)
      SELECT $1,$2,$3,$4,$5::timestamptz,$6,$7,$8::uuid,$9
      WHERE $8::uuid IS NULL OR EXISTS (SELECT 1 FROM agenda_tasks WHERE owner_id=$1 AND id=$8::uuid)
      ON CONFLICT (owner_id, id) DO UPDATE SET id=excluded.id
      WHERE agenda_ai_recoveries.provider=excluded.provider AND agenda_ai_recoveries.window_kind=excluded.window_kind
        AND agenda_ai_recoveries.expected_at=excluded.expected_at AND agenda_ai_recoveries.decision=excluded.decision
        AND agenda_ai_recoveries.alternative IS NOT DISTINCT FROM excluded.alternative
        AND agenda_ai_recoveries.task_id IS NOT DISTINCT FROM excluded.task_id AND NOT agenda_ai_recoveries.cancelled
      RETURNING id`, [ownerId, input.id, item.provider, item.window, item.expectedAt, item.decision, item.alternative, item.taskId, item.cancelled]);
    return rowCount ? json({ id: input.id }, 201) : failure(409);
  });
}
export function PUT(request: Request) {
  return withOwner(request, async () => {
    const input = await body(request);
    if (!input || typeof input.id !== "string" || !uuid.test(input.id)
      || !Number.isInteger(input.version) || (input.version as number) < 1 || !valid(input.recovery)) return failure(400);
    const item = input.recovery;
    if (!item.cancelled && Date.parse(item.expectedAt) <= Date.now()) {
      const existing = await database().query(`SELECT 1 FROM agenda_ai_recoveries WHERE owner_id=$1 AND id=$2
        AND version=$3+1 AND provider=$4 AND window_kind=$5 AND expected_at=$6::timestamptz
        AND decision=$7 AND alternative IS NOT DISTINCT FROM $8 AND task_id IS NOT DISTINCT FROM $9::uuid
        AND cancelled=$10`, [ownerId, input.id, input.version, item.provider, item.window, item.expectedAt,
        item.decision, item.alternative, item.taskId, item.cancelled]);
      return existing.rowCount ? json({ id: input.id }) : failure(400);
    }
    const { rowCount } = await database().query(`UPDATE agenda_ai_recoveries SET
      provider=$3, window_kind=$4, expected_at=$5::timestamptz, decision=$6, alternative=$7, task_id=$8::uuid,
      cancelled=$9, version=CASE WHEN version=$10 THEN version+1 ELSE version END
      WHERE owner_id=$1 AND id=$2 AND ($8::uuid IS NULL OR EXISTS
        (SELECT 1 FROM agenda_tasks WHERE owner_id=$1 AND id=$8::uuid))
        AND (version=$10 OR (version=$10+1 AND provider=$3 AND window_kind=$4 AND expected_at=$5::timestamptz
          AND decision=$6 AND alternative IS NOT DISTINCT FROM $7 AND task_id IS NOT DISTINCT FROM $8::uuid AND cancelled=$9))
      RETURNING id`, [ownerId, input.id, item.provider, item.window, item.expectedAt, item.decision, item.alternative, item.taskId, item.cancelled, input.version]);
    return rowCount ? json({ id: input.id }) : failure(409);
  });
}
