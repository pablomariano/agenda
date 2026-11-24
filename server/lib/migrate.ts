import { readFile } from "node:fs/promises";
import { database } from "./db.js";

try {
  const pool = database();
  try {
    await pool.query(await readFile(new URL("../sql/001_projects_and_tasks.sql", import.meta.url), "utf8"));
    await pool.query(await readFile(new URL("../sql/002_task_time.sql", import.meta.url), "utf8"));
    await pool.query(await readFile(new URL("../sql/003_ai_recoveries.sql", import.meta.url), "utf8"));
    await pool.query(await readFile(new URL("../sql/004_task_blocking.sql", import.meta.url), "utf8"));
    await pool.query(await readFile(new URL("../sql/005_daily_priorities.sql", import.meta.url), "utf8"));
    await pool.query(await readFile(new URL("../sql/006_weekly_goals.sql", import.meta.url), "utf8"));
    await pool.query(await readFile(new URL("../sql/007_ideas.sql", import.meta.url), "utf8"));
    console.log("Migraciones de agenda aplicadas.");
  } finally {
    await pool.end();
  }
} catch {
  console.error("No se pudo aplicar la migración. Revisa la conexión y los permisos de PostgreSQL.");
  process.exitCode = 1;
}
