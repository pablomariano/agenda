export function GET() {
  return Response.json(
    { status: "ok", configured: Boolean(process.env.DATABASE_URL) },
    { headers: { "Cache-Control": "no-store" } },
  );
}
