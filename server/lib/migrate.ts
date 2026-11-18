import { readFile } from "node:fs/promises";
import { database } from "./db.ts";

try {
  const pool = database();
  try {
    await pool.query(await readFile(new URL("../sql/001_projects_and_tasks.sql", import.meta.url), "utf8"));
    console.log("Migración de proyectos y tareas aplicada.");
  } finally {
    await pool.end();
  }
} catch {
  console.error("No se pudo aplicar la migración. Revisa la conexión y los permisos de PostgreSQL.");
  process.exitCode = 1;
}
