// Stub: recibe el payload del trigger (pg_net) o webhooks.
// Supabase Edge Functions ejecutan en Deno; el cliente de la app sigue en Kotlin.

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response(null, {
      status: 204,
      headers: {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Headers":
          "authorization, x-client-info, apikey, content-type",
      },
    });
  }

  let body: unknown = null;
  try {
    body = await req.json();
  } catch {
    body = null;
  }

  console.log("inventory-event-notify stub", JSON.stringify(body));

  return new Response(
    JSON.stringify({ ok: true, stub: true, echo: body }),
    {
      status: 200,
      headers: {
        "Content-Type": "application/json",
        "Access-Control-Allow-Origin": "*",
      },
    },
  );
});
