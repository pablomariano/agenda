import { strict as assert } from "node:assert";
import { randomBytes, randomUUID } from "node:crypto";
import { readFile } from "node:fs/promises";
import { test } from "node:test";
import { GET, POST, PUT } from "../api/agenda.js";
import { GET as listRecoveries, POST as createRecovery, PUT as editRecovery } from "../api/recoveries.js";
import { GET as readPriorities, PUT as savePriorities } from "../api/priorities.js";
import { GET as readGoals, POST as createGoal, PUT as editGoal } from "../api/weekly-goals.js";
import { GET as listIdeas, POST as createIdea, PUT as convertIdea } from "../api/ideas.js";
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
    await pool.query(await readFile(new URL("../sql/002_task_time.sql", import.meta.url), "utf8"));
    await pool.query(await readFile(new URL("../sql/003_ai_recoveries.sql", import.meta.url), "utf8"));
    await pool.query(await readFile(new URL("../sql/003_ai_recoveries.sql", import.meta.url), "utf8"));
    await pool.query(await readFile(new URL("../sql/004_task_blocking.sql", import.meta.url), "utf8"));
    await pool.query(await readFile(new URL("../sql/004_task_blocking.sql", import.meta.url), "utf8"));
    await pool.query(await readFile(new URL("../sql/005_daily_priorities.sql", import.meta.url), "utf8"));
    await pool.query(await readFile(new URL("../sql/005_daily_priorities.sql", import.meta.url), "utf8"));
    await pool.query(await readFile(new URL("../sql/006_weekly_goals.sql", import.meta.url), "utf8"));
    await pool.query(await readFile(new URL("../sql/006_weekly_goals.sql", import.meta.url), "utf8"));
    await pool.query(await readFile(new URL("../sql/007_ideas.sql", import.meta.url), "utf8"));
    await pool.query(await readFile(new URL("../sql/007_ideas.sql", import.meta.url), "utf8"));
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

    const ideaRequest = (method: string, payload?: object, secret = code) => new Request("https://agenda.test/api/ideas", {
      method, headers: { authorization: `Bearer ${secret}`, ...(payload ? { "content-type": "application/json" } : {}) },
      ...(payload ? { body: JSON.stringify(payload) } : {}),
    });
    const idea = { id: randomUUID(), content: "Una idea con detalles\ny una segunda línea" };
    assert.equal((await listIdeas(ideaRequest("GET", undefined, otherCode))).status, 401);
    assert.equal((await createIdea(ideaRequest("POST", { ...idea, content: "  " }))).status, 400);
    assert.equal((await createIdea(ideaRequest("POST", idea))).status, 201);
    assert.equal((await createIdea(ideaRequest("POST", idea))).status, 201);
    assert.equal((await createIdea(ideaRequest("POST", { ...idea, content: "Diferente" }))).status, 409);
    const conversion = { id: idea.id, resultId: randomUUID(), kind: "task", projectId: project.id, title: "Tarea desde idea", level: "alta" };
    assert.equal((await convertIdea(ideaRequest("PUT", { ...conversion, projectId: randomUUID() }))).status, 409);
    await pool.query(`CREATE OR REPLACE FUNCTION agenda_test_fail_conversion() RETURNS trigger LANGUAGE plpgsql AS $$
      BEGIN RAISE EXCEPTION 'fallo simulado'; END $$`);
    await pool.query(`CREATE TRIGGER agenda_test_fail_conversion BEFORE UPDATE OF conversion_request ON agenda_ideas
      FOR EACH ROW EXECUTE FUNCTION agenda_test_fail_conversion()`);
    try {
      assert.equal((await convertIdea(ideaRequest("PUT", conversion))).status, 503);
      assert.equal((await pool.query("SELECT count(*)::int AS n FROM agenda_tasks WHERE id=$1", [conversion.resultId])).rows[0].n, 0);
      assert.ok((await (await listIdeas(ideaRequest("GET"))).json()).ideas.some((i: { id: string; content: string }) => i.id === idea.id && i.content === idea.content));
    } finally {
      await pool.query("DROP TRIGGER IF EXISTS agenda_test_fail_conversion ON agenda_ideas");
      await pool.query("DROP FUNCTION IF EXISTS agenda_test_fail_conversion()");
    }
    const concurrentIdeas = await Promise.all([convertIdea(ideaRequest("PUT", conversion)), convertIdea(ideaRequest("PUT", conversion))]);
    assert.deepEqual(concurrentIdeas.map(r => r.status), [200, 200]);
    assert.equal((await convertIdea(ideaRequest("PUT", { ...conversion, resultId: randomUUID() }))).status, 409);
    const convertedTask = (await pool.query("SELECT title,description FROM agenda_tasks WHERE id=$1", [conversion.resultId])).rows;
    assert.deepEqual(convertedTask, [{ title: conversion.title, description: idea.content }]);
    assert.ok(!(await (await listIdeas(ideaRequest("GET"))).json()).ideas.some((i: { id: string }) => i.id === idea.id));
    const projectIdea = { id: randomUUID(), content: "Idea para nuevo proyecto: conservar todo" };
    assert.equal((await createIdea(ideaRequest("POST", projectIdea))).status, 201);
    const projectConversion = { id: projectIdea.id, resultId: randomUUID(), kind: "project", projectId: null, title: "Proyecto desde idea", level: "media" };
    assert.equal((await convertIdea(ideaRequest("PUT", projectConversion))).status, 200);
    assert.equal((await convertIdea(ideaRequest("PUT", projectConversion))).status, 200);
    assert.deepEqual((await pool.query("SELECT title,description FROM agenda_projects WHERE id=$1", [projectConversion.resultId])).rows,
      [{ title: projectConversion.title, description: projectIdea.content }]);
    const withDescriptions = await (await GET(request(code))).json();
    assert.ok(withDescriptions.tasks.some((t: { id: string; description: string }) => t.id === conversion.resultId && t.description === idea.content));
    assert.ok(withDescriptions.projects.some((p: { id: string; description: string }) => p.id === projectConversion.resultId && p.description === projectIdea.content));

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
    const tieA = { ...task, id: randomUUID(), title: "Empate A" };
    const tieB = { ...task, id: randomUUID(), title: "Empate B" };
    assert.equal((await POST(request(code, tieA))).status, 201);
    assert.equal((await POST(request(code, tieB))).status, 201);
    await pool.query("UPDATE agenda_tasks SET created_at='2026-01-01T00:00:00Z' WHERE id=ANY($1::uuid[])", [[tieA.id, tieB.id]]);
    const tieIds = new Set<string>([tieA.id, tieB.id]);
    const ties = (await (await GET(request(code))).json()).tasks.filter((t: { id: string }) => tieIds.has(t.id));
    assert.deepEqual(ties.map((t: { id: string }) => t.id), [tieA.id, tieB.id].sort());

    const original = { title: task.title, projectId: task.projectId, impact: task.level, completed: false };
    const edited = { title: "Propuesta revisada", projectId: urgent.id, impact: "media", completed: false };
    const patch = (secret: string, expected: object, next: object) => new Request(url, {
      method: "PUT", headers: { authorization: `Bearer ${secret}`, "content-type": "application/json" },
      body: JSON.stringify({ id: task.id, expected, task: next }),
    });
    const noBlock = { ...original, blockedBy: null };
    const codexBlock = { ...original, blockedBy: "codex" };
    assert.equal((await PUT(patch(code, noBlock, { ...codexBlock, blockedBy: "unknown" }))).status, 400);
    assert.equal((await PUT(patch(code, noBlock, codexBlock))).status, 200);
    assert.equal((await PUT(patch(code, noBlock, codexBlock))).status, 200);
    assert.equal((await PUT(patch(code, noBlock, { ...original, blockedBy: "gemini" }))).status, 409);
    assert.equal((await PUT(patch(code, original, original))).status, 200);
    assert.equal((await (await GET(request(code))).json()).tasks.find((t: { id: string }) => t.id === task.id).blockedBy, "codex");
    assert.equal((await PUT(patch(code, codexBlock, noBlock))).status, 200);
    assert.equal((await PUT(patch(otherCode, original, edited))).status, 401);
    assert.equal((await PUT(patch(code, original, { ...edited, projectId: foreignProject }))).status, 409);
    assert.equal((await PUT(patch(code, original, { ...edited, title: "" }))).status, 400);
    assert.equal((await PUT(patch(code, original, edited))).status, 200);
    assert.equal((await PUT(patch(code, original, edited))).status, 200);
    assert.equal((await PUT(patch(code, original, { ...edited, title: "Obsoleto" }))).status, 409);
    const completed = { ...edited, completed: true };
    assert.equal((await PUT(patch(code, edited, completed))).status, 200);
    assert.equal((await PUT(patch(code, edited, completed))).status, 200);
    const withCompleted = await (await GET(request(code))).json();
    assert.ok(!withCompleted.tasks.some((t: { id: string }) => t.id === task.id));
    assert.ok(withCompleted.completedTasks.some((t: { id: string; completedToday: boolean }) => t.id === task.id && t.completedToday));
    assert.equal((await PUT(patch(code, completed, edited))).status, 200);
    assert.ok((await (await GET(request(code))).json()).tasks.some((t: { id: string }) => t.id === task.id));

    const dates = (await pool.query(`SELECT to_char(timezone('America/Santiago', now())::date - 4, 'YYYY-MM-DD') AS past,
      to_char(timezone('America/Santiago', now())::date, 'YYYY-MM-DD') AS today,
      to_char(timezone('America/Santiago', now())::date + 1, 'YYYY-MM-DD') AS tomorrow`)).rows[0];
    const planned = { ...task, id: randomUUID(), plannedOn: dates.tomorrow, plannedTime: "09:30" };
    assert.equal((await POST(request(code, { ...planned, plannedOn: "2026-02-30" }))).status, 400);
    assert.equal((await POST(request(code, { ...planned, plannedTime: "25:00" }))).status, 400);
    assert.equal((await POST(request(code, planned))).status, 201);
    assert.equal((await POST(request(code, planned))).status, 201);
    assert.equal((await POST(request(code, { ...planned, plannedTime: null }))).status, 409);
    let planSnapshot = await (await GET(request(code))).json();
    assert.ok(!planSnapshot.tasks.some((t: { id: string }) => t.id === planned.id));
    assert.ok(planSnapshot.futureTasks.some((t: { id: string; plannedTime: string }) => t.id === planned.id && t.plannedTime === "09:30"));
    await pool.query("UPDATE agenda_tasks SET planned_on = $1::date, blocked_by = 'gemini' WHERE id = $2", [dates.past, planned.id]);
    planSnapshot = await (await GET(request(code))).json();
    const overdue = planSnapshot.tasks.filter((t: { id: string }) => t.id === planned.id);
    assert.equal(overdue.length, 1);
    assert.ok(overdue[0].carriedOver && overdue[0].plannedOn === dates.past && overdue[0].blockedBy === "gemini");
    const planBefore = { title: planned.title, projectId: planned.projectId, impact: planned.level, completed: false, plannedOn: dates.past, plannedTime: "09:30" };
    const planAfter = { ...planBefore, plannedOn: dates.today, plannedTime: null };
    const planRequest = (expected: object, next: object) => new Request(url, {
      method: "PUT", headers: { authorization: `Bearer ${code}`, "content-type": "application/json" },
      body: JSON.stringify({ id: planned.id, expected, task: next }),
    });
    assert.equal((await PUT(planRequest(planBefore, planAfter))).status, 200);
    assert.equal((await PUT(planRequest(planBefore, planAfter))).status, 200);
    assert.equal((await PUT(planRequest(planBefore, { ...planAfter, plannedTime: "11:00" }))).status, 409);
    planSnapshot = await (await GET(request(code))).json();
    assert.ok(planSnapshot.tasks.some((t: { id: string; plannedTime: string | null; carriedOver: boolean; blockedBy: string }) => t.id === planned.id && t.plannedTime === null && !t.carriedOver && t.blockedBy === "gemini"));
    const oldClient = { title: planned.title, projectId: planned.projectId, impact: planned.level, completed: false };
    assert.equal((await PUT(planRequest(oldClient, { ...oldClient, title: "Editada por versión anterior" }))).status, 200);
    planSnapshot = await (await GET(request(code))).json();
    assert.ok(planSnapshot.tasks.some((t: { id: string; plannedOn: string; plannedTime: string | null }) => t.id === planned.id && t.plannedOn === dates.today && t.plannedTime === null));

    const recoveryId = randomUUID();
    const recovery = { provider: "codex", window: "short", expectedAt: new Date(Date.now() + 18_000_000).toISOString(),
      decision: "continue", alternative: "gemini", taskId: task.id, cancelled: false };
    const recoveryRequest = (method: string, payload?: object, secret = code) => new Request("https://agenda.test/api/recoveries", {
      method, headers: { authorization: `Bearer ${secret}`, ...(payload ? { "content-type": "application/json" } : {}) },
      ...(payload ? { body: JSON.stringify(payload) } : {}),
    });
    const create = { id: recoveryId, recovery };
    assert.equal((await createRecovery(recoveryRequest("POST", create, otherCode))).status, 401);
    assert.equal((await createRecovery(recoveryRequest("POST", { ...create, recovery: { ...recovery, alternative: "codex" } }))).status, 400);
    assert.equal((await createRecovery(recoveryRequest("POST", { ...create, recovery: { ...recovery, expectedAt: "2026-02-30T00:00:00.000Z" } }))).status, 400);
    assert.equal((await createRecovery(recoveryRequest("POST", { ...create, recovery: { ...recovery, expectedAt: "2020-01-01T00:00:00.000Z" } }))).status, 400);
    assert.equal((await createRecovery(recoveryRequest("POST", { ...create, recovery: { ...recovery, taskId: randomUUID() } }))).status, 409);
    assert.equal((await createRecovery(recoveryRequest("POST", create))).status, 201);
    assert.equal((await createRecovery(recoveryRequest("POST", create))).status, 201);
    assert.equal((await createRecovery(recoveryRequest("POST", { ...create, recovery: { ...recovery, window: "weekly" } }))).status, 409);
    const weekly = { id: randomUUID(), recovery: { ...recovery, window: "weekly", taskId: null } };
    assert.equal((await createRecovery(recoveryRequest("POST", weekly))).status, 201);
    const editedBlocked = { ...edited, blockedBy: "codex" };
    assert.equal((await PUT(patch(code, { ...edited, blockedBy: null }, editedBlocked))).status, 200);
    await pool.query("UPDATE agenda_ai_recoveries SET expected_at=now()-interval '1 minute' WHERE owner_id=$1 AND id=$2", [ownerId, recoveryId]);
    assert.equal((await (await GET(request(code))).json()).tasks.find((t: { id: string }) => t.id === task.id).blockedBy, "codex");
    await pool.query("UPDATE agenda_ai_recoveries SET expected_at=$1 WHERE owner_id=$2 AND id=$3", [recovery.expectedAt, ownerId, recoveryId]);
    assert.equal((await PUT(patch(code, edited, { ...edited, completed: true }))).status, 200);
    assert.ok((await (await listRecoveries(recoveryRequest("GET"))).json()).recoveries.some((entry: { id: string; cancelled: boolean }) => entry.id === recoveryId && !entry.cancelled));
    const changed = { ...recovery, expectedAt: new Date(Date.now() + 21_600_000).toISOString() };
    const update = { id: recoveryId, version: 1, recovery: changed };
    assert.equal((await editRecovery(recoveryRequest("PUT", update))).status, 200);
    assert.equal((await editRecovery(recoveryRequest("PUT", update))).status, 200);
    assert.equal((await editRecovery(recoveryRequest("PUT", { ...update, recovery }))).status, 409);
    const originalNow = Date.now;
    try {
      Date.now = () => Date.parse(changed.expectedAt) + 1;
      assert.equal((await editRecovery(recoveryRequest("PUT", update))).status, 200);
      assert.equal((await editRecovery(recoveryRequest("PUT", { ...update, recovery: { ...changed, decision: "wait", alternative: null } }))).status, 400);
    } finally { Date.now = originalNow; }
    assert.equal((await editRecovery(recoveryRequest("PUT", { id: recoveryId, version: 2, recovery: { ...changed, cancelled: true } }))).status, 200);
    const savedRecoveries = await (await listRecoveries(recoveryRequest("GET"))).json();
    assert.ok(savedRecoveries.recoveries.some((entry: { id: string; cancelled: boolean; version: number }) => entry.id === recoveryId && entry.cancelled && entry.version === 3));
    assert.ok(savedRecoveries.recoveries.some((entry: { id: string; cancelled: boolean }) => entry.id === weekly.id && !entry.cancelled));
    const expired = { ...weekly.recovery, expectedAt: "2020-01-01T00:00:00.000Z" };
    await pool.query("UPDATE agenda_ai_recoveries SET expected_at=$1 WHERE owner_id=$2 AND id=$3", [expired.expectedAt, ownerId, weekly.id]);
    assert.equal((await createRecovery(recoveryRequest("POST", { ...weekly, recovery: expired }))).status, 201);
    assert.equal((await createRecovery(recoveryRequest("POST", { id: randomUUID(), recovery: expired }))).status, 400);

    const projectBefore = { title: project.title, importance: project.level };
    const projectAfter = { title: "Proyecto renombrado", importance: "alta" };
    const projectEdit = (expected: object, next: object, secret = code) => new Request(url, {
      method: "PUT", headers: { authorization: `Bearer ${secret}`, "content-type": "application/json" },
      body: JSON.stringify({ kind: "project", id: project.id, expected, project: next }),
    });
    assert.equal((await PUT(projectEdit(projectBefore, projectAfter, otherCode))).status, 401);
    assert.equal((await PUT(projectEdit(projectBefore, { ...projectAfter, title: " " }))).status, 400);
    assert.equal((await PUT(projectEdit(projectBefore, { ...projectAfter, importance: "urgente" }))).status, 400);
    assert.equal((await PUT(projectEdit(projectBefore, projectAfter))).status, 200);
    assert.equal((await PUT(projectEdit(projectBefore, projectAfter))).status, 200);
    assert.equal((await PUT(projectEdit(projectBefore, { ...projectAfter, title: "Cambio obsoleto" }))).status, 409);
    const afterProject = await (await GET(request(code))).json();
    assert.ok(afterProject.projects.some((p: { id: string; title: string; importance: string }) => p.id === project.id && p.title === projectAfter.title && p.importance === "alta"));

    const priorityRequest = (method: string, payload?: object, secret = code) => new Request("https://agenda.test/api/priorities", {
      method, headers: { authorization: `Bearer ${secret}`, ...(payload ? { "content-type": "application/json" } : {}) },
      ...(payload ? { body: JSON.stringify(payload) } : {}),
    });
    assert.equal((await readPriorities(priorityRequest("GET", undefined, otherCode))).status, 401);
    const priorityState = await (await readPriorities(priorityRequest("GET"))).json();
    const prioritySelection = { day: priorityState.day, version: priorityState.version, ids: [task.id, urgentTask.id] };
    assert.equal((await savePriorities(priorityRequest("PUT", { ...prioritySelection, ids: [task.id, task.id] }))).status, 400);
    assert.equal((await savePriorities(priorityRequest("PUT", { ...prioritySelection, ids: [randomUUID()] }))).status, 409);
    assert.equal((await savePriorities(priorityRequest("PUT", { ...prioritySelection, day: dates.tomorrow }))).status, 409);
    assert.equal((await savePriorities(priorityRequest("PUT", prioritySelection))).status, 200);
    assert.equal((await savePriorities(priorityRequest("PUT", prioritySelection))).status, 200);
    assert.equal((await savePriorities(priorityRequest("PUT", { ...prioritySelection, ids: [urgentTask.id, task.id] }))).status, 409);
    const prioritiesSaved = await (await readPriorities(priorityRequest("GET"))).json();
    assert.deepEqual(prioritiesSaved.ids, [task.id, urgentTask.id]);
    assert.equal(prioritiesSaved.version, priorityState.version + 1);
    const urgentBefore = { title: urgentTask.title, projectId: urgentTask.projectId, impact: urgentTask.level, completed: false };
    const urgentUpdate = (expected: object, next: object) => new Request(url, {
      method: "PUT", headers: { authorization: `Bearer ${code}`, "content-type": "application/json" },
      body: JSON.stringify({ id: urgentTask.id, expected, task: next }),
    });
    assert.equal((await PUT(urgentUpdate(urgentBefore, { ...urgentBefore, completed: true }))).status, 200);
    assert.deepEqual((await (await readPriorities(priorityRequest("GET"))).json()).ids, [task.id, urgentTask.id]);
    assert.ok((await (await GET(request(code))).json()).completedTasks.some((t: { id: string; completedToday: boolean }) => t.id === urgentTask.id && t.completedToday));
    assert.equal((await PUT(urgentUpdate({ ...urgentBefore, completed: true }, urgentBefore))).status, 200);
    assert.equal((await PUT(projectEdit(projectAfter, { ...projectAfter, importance: "baja" }))).status, 200);
    assert.deepEqual((await (await readPriorities(priorityRequest("GET"))).json()).ids, [task.id, urgentTask.id]);
    const suggestionsAfterImportance = (await (await GET(request(code))).json()).tasks.map((t: { id: string }) => t.id);
    assert.ok(suggestionsAfterImportance.indexOf(urgentTask.id) < suggestionsAfterImportance.indexOf(planned.id));
    const orderChange = { ...prioritiesSaved, ids: [urgentTask.id, task.id] };
    assert.equal((await savePriorities(priorityRequest("PUT", orderChange))).status, 200);
    assert.deepEqual((await (await readPriorities(priorityRequest("GET"))).json()).ids, [urgentTask.id, task.id]);
    const concurrentBase = await (await readPriorities(priorityRequest("GET"))).json();
    const concurrent = await Promise.all([
      savePriorities(priorityRequest("PUT", { ...concurrentBase, ids: [task.id] })),
      savePriorities(priorityRequest("PUT", { ...concurrentBase, ids: [urgentTask.id] })),
    ]);
    assert.deepEqual(concurrent.map(result => result.status).sort(), [200, 409]);

    const goalRequest = (method: string, payload?: object, week = "2026-11-23", secret = code) => new Request(`https://agenda.test/api/weekly-goals?week=${week}`, {
      method, headers: { authorization: `Bearer ${secret}`, ...(payload ? { "content-type": "application/json" } : {}) },
      ...(payload ? { body: JSON.stringify(payload) } : {}),
    });
    const goal = { id: randomUUID(), weekStart: "2026-11-23", title: "Planificar semana" };
    assert.equal((await readGoals(goalRequest("GET", undefined, goal.weekStart, otherCode))).status, 401);
    assert.equal((await readGoals(goalRequest("GET", undefined, "2026-11-24"))).status, 400);
    assert.equal((await createGoal(goalRequest("POST", { ...goal, weekStart: "2026-02-30" }))).status, 400);
    assert.equal((await createGoal(goalRequest("POST", { ...goal, title: " " }))).status, 400);
    assert.equal((await createGoal(goalRequest("POST", goal))).status, 201);
    assert.equal((await createGoal(goalRequest("POST", goal))).status, 201);
    assert.equal((await createGoal(goalRequest("POST", { ...goal, title: "Otro" }))).status, 409);
    assert.equal((await editGoal(goalRequest("PUT", { id: goal.id, version: 1, title: "Revisar semana" }))).status, 200);
    assert.equal((await editGoal(goalRequest("PUT", { id: goal.id, version: 1, title: "Revisar semana" }))).status, 200);
    assert.equal((await editGoal(goalRequest("PUT", { id: goal.id, version: 1, title: "Edición vieja" }))).status, 409);
    assert.ok((await (await readGoals(goalRequest("GET"))).json()).goals.some((g: { id: string; title: string; version: number }) => g.id === goal.id && g.title === "Revisar semana" && g.version === 2));
    assert.ok(!(await (await readGoals(goalRequest("GET", undefined, "2026-11-30"))).json()).goals.some((g: { id: string }) => g.id === goal.id));

    await setCode(otherCode);
    assert.equal((await GET(request(code))).status, 401);
    assert.equal((await listRecoveries(recoveryRequest("GET"))).status, 401);
    const afterRotation = await (await GET(request(otherCode))).json();
    assert.ok(afterRotation.completedTasks.some((t: { id: string }) => t.id === task.id));
    const recoveriesAfterRotation = await (await listRecoveries(recoveryRequest("GET", undefined, otherCode))).json();
    assert.ok(recoveriesAfterRotation.recoveries.some((entry: { id: string }) => entry.id === weekly.id));
  } finally {
    await pool.end();
    if (previous === undefined) delete process.env.DATABASE_URL;
    else process.env.DATABASE_URL = previous;
  }
});
