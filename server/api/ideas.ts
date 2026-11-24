import { accessCode, accessStatus, ownerId } from "../lib/auth.js";
import { database } from "../lib/db.js";

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const errors = { 400: "Revisa los datos de la idea.", 401: "Introduce tu código de acceso vigente.", 409: "La idea cambió o no se pudo convertir. Actualiza antes de intentar otra vez.", 503: "La agenda no está disponible por ahora." };
function json(value: unknown, status = 200) { return Response.json(value, { status, headers: { "Cache-Control": "no-store" } }); }
function failure(status: keyof typeof errors) { return json({ error: errors[status] }, status); }
async function withOwner(request: Request, action: () => Promise<Response>) {
  if (!process.env.DATABASE_URL) return failure(503);
  const code = accessCode(request);
  if (!code) return failure(401);
  try { const status = await accessStatus(code); return status === 200 ? await action() : failure(status); }
  catch { return failure(503); }
}
async function body(request: Request): Promise<Record<string, unknown> | null> {
  if (request.headers.get("content-type")?.split(";")[0]?.trim().toLowerCase() !== "application/json") return null;
  try {
    const raw = await request.text();
    if (raw.length > 32_768) return null;
    const value: unknown = JSON.parse(raw);
    return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : null;
  } catch { return null; }
}
export function GET(request: Request) {
  return withOwner(request, async () => {
    const { rows } = await database().query(`SELECT id, content FROM agenda_ideas
      WHERE owner_id=$1 AND conversion_request IS NULL ORDER BY created_at DESC, id`, [ownerId]);
    return json({ ideas: rows });
  });
}
export function POST(request: Request) {
  return withOwner(request, async () => {
    const input = await body(request);
    if (!input || typeof input.id !== "string" || !uuid.test(input.id) || typeof input.content !== "string"
      || !input.content.trim() || [...input.content].length > 4000 || input.content.includes("\u0000")) return failure(400);
    const { rowCount } = await database().query(`INSERT INTO agenda_ideas(owner_id,id,content) VALUES ($1,$2,$3)
      ON CONFLICT (owner_id,id) DO UPDATE SET id=excluded.id
      WHERE agenda_ideas.content=excluded.content RETURNING id`, [ownerId, input.id, input.content]);
    return rowCount ? json({ id: input.id }, 201) : failure(409);
  });
}
export function PUT(request: Request) {
  return withOwner(request, async () => {
    const input = await body(request);
    if (!input || typeof input.id !== "string" || !uuid.test(input.id)
      || typeof input.resultId !== "string" || !uuid.test(input.resultId)
      || (input.kind !== "project" && input.kind !== "task")
      || typeof input.title !== "string" || !input.title.trim() || [...input.title.trim()].length > 120 || input.title.includes("\u0000")
      || typeof input.level !== "string" || !["alta", "media", "baja"].includes(input.level)
      || (input.kind === "task" ? typeof input.projectId !== "string" || !uuid.test(input.projectId) : input.projectId !== null)) return failure(400);
    const conversion = { resultId: input.resultId.toLowerCase(), kind: input.kind, title: input.title.trim(), level: input.level,
      projectId: typeof input.projectId === "string" ? input.projectId.toLowerCase() : null };
    const client = await database().connect();
    try {
      await client.query("BEGIN");
      const { rows: [idea] } = await client.query("SELECT content, conversion_request FROM agenda_ideas WHERE owner_id=$1 AND id=$2 FOR UPDATE", [ownerId, input.id]);
      if (!idea) { await client.query("ROLLBACK"); return failure(409); }
      if (idea.conversion_request !== null) {
        const { rows: [same] } = await client.query("SELECT $1::jsonb = $2::jsonb AS matches", [JSON.stringify(idea.conversion_request), JSON.stringify(conversion)]);
        await client.query("ROLLBACK");
        return same.matches ? json({ id: conversion.resultId, kind: conversion.kind }) : failure(409);
      }
      if (conversion.kind === "project") {
        const result = await client.query(`INSERT INTO agenda_projects(owner_id,id,title,importance,description)
          VALUES ($1,$2,$3,$4,$5) ON CONFLICT DO NOTHING RETURNING id`, [ownerId, conversion.resultId, conversion.title, conversion.level, idea.content]);
        if (!result.rowCount) { await client.query("ROLLBACK"); return failure(409); }
      } else {
        const result = await client.query(`INSERT INTO agenda_tasks(owner_id,id,project_id,title,impact,description)
          SELECT $1,$2,id,$3,$4,$5 FROM agenda_projects WHERE owner_id=$1 AND id=$6
          ON CONFLICT DO NOTHING RETURNING id`, [ownerId, conversion.resultId, conversion.title, conversion.level, idea.content, conversion.projectId]);
        if (!result.rowCount) { await client.query("ROLLBACK"); return failure(409); }
      }
      await client.query(`UPDATE agenda_ideas SET converted_project_id=$3::uuid, converted_task_id=$4::uuid, conversion_request=$5::jsonb
        WHERE owner_id=$1 AND id=$2`, [ownerId, input.id, conversion.kind === "project" ? conversion.resultId : null,
        conversion.kind === "task" ? conversion.resultId : null, JSON.stringify(conversion)]);
      await client.query("COMMIT");
      return json({ id: conversion.resultId, kind: conversion.kind });
    } catch (error) { await client.query("ROLLBACK"); throw error; }
    finally { client.release(); }
  });
}
