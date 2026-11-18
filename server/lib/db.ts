import { Pool } from "pg";

let pool: Pool | undefined;

export function database(): Pool {
  const connectionString = process.env.DATABASE_URL;
  if (!connectionString) throw new Error("Falta configurar la base de datos.");
  return pool ??= new Pool({
    connectionString,
    max: 3,
    connectionTimeoutMillis: 5_000,
    idleTimeoutMillis: 5_000,
    query_timeout: 8_000,
  }).on("error", () => console.error("Se perdió una conexión inactiva a PostgreSQL."));
}
