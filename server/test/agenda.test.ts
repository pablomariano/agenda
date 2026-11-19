import { strict as assert } from "node:assert";
import { randomBytes, randomUUID } from "node:crypto";
import { readFile } from "node:fs/promises";
import { test } from "node:test";
import { GET, POST } from "../api/agenda.js";
import { GET as health } from "../api/health.js";
import { accessHash, ownerId } from "../lib/auth.js";
import { database } from "../lib/db.js";

const url = "https://agenda.test/api/agenda";
const testDatabase = process.env.TEST_DATABASE_URL;

test("sin configuración, la API no expone datos ni valores privados", async () => {
  const previous = process.env.DATABASE_URL;
  delete process.env.DATABASE_URL;
  try {
    assert.deepEqual(await health().json(), { status: "ok", configured: false });
    const result = await GET(new Request(url));
    assert.equal(result.status, 503);
    assert.deepEqual(await result.json(), { error: "La agenda no está disponible por ahora." });
  } finally {
    if (previous !== undefined) process.env.DATABASE_URL = previous;
  }
});

test("acceso, persistencia, orden y reintentos con PostgreSQL", { skip: !testDatabase }, async () => {
  assert.ok(testDatabase);
  const target = new URL(testDatabase);
  assert.equal(target.hostname, "127.0.0.1", "Usa una base de pruebas local.");
  assert.equal(target.pathname, "/agenda_test", "Usa la base dedicada agenda_test.");
  const previous = process.env.DATABASE_URL;
  process.env.DATABASE_URL = testDatabase;
  const pool = database();
  const code = randomBytes(32).toString("hex");
  const otherCode = randomBytes(32).toString("hex");
  const project = { kind: "project", id: randomUUID(), title: "Proyecto de prueba", level: "media" };
  const urgent = { kind: "project", id: randomUUID(), title: "Prioritario", level: "alta" };
  const task = { kind: "task", id: randomUUID(), projectId: project.id, title: "Preparar propuesta", level: "alta" };
  const urgentTask = { ...task, id: randomUUID(), title: "Enviar cifras", projectId: urgent.id, level: "baja" };
  const foreignProject = randomUUID();
  const foreignOwner = randomUUID();
  const request = (secret?: string, post?: object) => new Request(url, {
    method: post ? "POST" : "GET",
    headers: {
      ...(secret === undefined ? {} : { authorization: `Bearer ${secret}` }),
      ...(post ? { "content-type": "application/json" } : {}),
    },
    body: post ? JSON.stringify(post) : undefined,
  });
  const setCode = async (value: string) => pool.query(
    `INSERT INTO agenda_access (owner_id, token_sha256) VALUES ($1, $2)
    ON CONFLICT (owner_id) DO UPDATE SET token_sha256 = excluded.token_sha256`, [ownerId, accessHash(value)],
  );
  try {
    await pool.query(await readFile(new URL("../sql/001_projects_and_tasks.sql", import.meta.url), "utf8"));
    await setCode(code);
    assert.equal((await GET(request())).status, 401);
    assert.equal((await GET(request("inválido"))).status, 401);
    assert.equal((await GET(request(otherCode))).status, 401);
    assert.equal((await POST(request(otherCode, project))).status, 401);
    assert.equal((await GET(request(code))).status, 200);

    assert.equal((await POST(request(code, { ...project, title: "  " }))).status, 400);
    assert.equal((await POST(request(code, { ...project, level: "urgente" }))).status, 400);
    assert.equal((await POST(request(code, project))).status, 201);
    assert.equal((await POST(request(code, urgent))).status, 201);

    await pool.query(
      "INSERT INTO agenda_projects (owner_id, id, title, importance) VALUES ($1, $2, 'Ajeno', 'alta')",
      [foreignOwner, foreignProject],
    );
    assert.equal((await POST(request(code, { ...task, projectId: foreignProject }))).status, 409);

    const repeats = await Promise.all([POST(request(code, task)), POST(request(code, task))]);
    assert.deepEqual(repeats.map(r => r.status), [201, 201]);
    assert.equal((await POST(request(code, { ...task, title: "Distinta" }))).status, 409);
    assert.equal((await POST(request(code, urgentTask))).status, 201);

    const snapshot = await GET(request(code));
    assert.equal(snapshot.headers.get("cache-control"), "no-store");
    const data = await snapshot.json();
    const projectIds = new Set<string>([project.id, urgent.id, foreignProject]);
    const taskIds = new Set<string>([task.id, urgentTask.id]);
    const ownProjects = data.projects.filter((p: { id: string }) => projectIds.has(p.id));
    assert.deepEqual(ownProjects.map((p: { id: string }) => p.id), [project.id, urgent.id]);
    const visible = data.tasks.filter((t: { id: string }) => taskIds.has(t.id));
    assert.deepEqual(visible.map((t: { id: string }) => t.id), [urgentTask.id, task.id]);
    assert.equal(visible[1].projectTitle, project.title);
    assert.equal(visible[1].impact, task.level);

    await setCode(otherCode);
    assert.equal((await GET(request(code))).status, 401);
    const afterRotation = await (await GET(request(otherCode))).json();
    assert.ok(afterRotation.tasks.some((t: { id: string }) => t.id === task.id));
  } finally {
    await pool.end();
    if (previous === undefined) delete process.env.DATABASE_URL;
    else process.env.DATABASE_URL = previous;
  }
});
