import { createHash, timingSafeEqual } from "node:crypto";
import { database } from "./db.js";

export const ownerId = "personal";

export function accessCode(request: Request): string | null {
  return /^Bearer ([0-9a-f]{64})$/i.exec(request.headers.get("authorization") ?? "")?.[1].toLowerCase() ?? null;
}

export function accessHash(code: string): Buffer {
  return createHash("sha256").update(code).digest();
}

export async function accessStatus(code: string): Promise<200 | 401 | 503> {
  const { rows: [access] } = await database().query<{ token_sha256: Buffer }>(
    "SELECT token_sha256 FROM agenda_access WHERE owner_id = $1",
    [ownerId],
  );
  if (!access) return 503;
  return timingSafeEqual(accessHash(code), access.token_sha256) ? 200 : 401;
}
