import { randomBytes } from "node:crypto";
import { accessHash, ownerId } from "./auth.js";
import { database } from "./db.js";

if (!process.stdout.isTTY) {
  console.error("Ejecuta este comando desde una terminal interactiva.");
  process.exit(1);
}

try {
  const pool = database();
  const code = randomBytes(32).toString("hex");
  try {
    await pool.query(`
      INSERT INTO agenda_access (owner_id, token_sha256) VALUES ($1, $2)
      ON CONFLICT (owner_id) DO UPDATE SET token_sha256 = excluded.token_sha256
    `, [ownerId, accessHash(code)]);
  } finally {
    await pool.end();
  }
  console.log("Guarda el código en tu gestor de contraseñas. El código anterior dejó de funcionar.");
  console.log(code);
} catch {
  console.error("No se pudo generar el código. Revisa la conexión y la migración de PostgreSQL.");
  process.exitCode = 1;
}
