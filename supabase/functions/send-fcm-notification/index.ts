// Supabase Edge Function: Firebase Cloud Messaging Push Dispatch
// Uses FCM HTTP v1 API with GOOGLE_ACCESS_TOKEN & FIREBASE_PROJECT_ID secrets.

import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.0";

serve(async (req) => {
  if (req.method !== "POST") return new Response("Method Not Allowed", { status: 405 });

  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  const fcmProjectId = Deno.env.get("FIREBASE_PROJECT_ID") ?? "";
  const fcmAccessToken = Deno.env.get("FIREBASE_ACCESS_TOKEN") ?? "";

  const supabase = createClient(supabaseUrl, serviceKey);
  const { team_id, title, body, event_type, target_user_ids } = await req.json();

  let query = supabase
    .from("team_members")
    .select("user_id, users!inner(fcm_token)")
    .eq("team_id", team_id)
    .eq("status", "ACTIVE");

  if (Array.isArray(target_user_ids) && target_user_ids.length > 0) {
    query = query.in("user_id", target_user_ids);
  }

  const { data: recipients } = await query;
  let sent = 0;

  for (const row of recipients ?? []) {
    const token = (row as { users?: { fcm_token?: string } }).users?.fcm_token;
    if (!token || !fcmProjectId || !fcmAccessToken) continue;

    const resp = await fetch(
      `https://fcm.googleapis.com/v1/projects/${fcmProjectId}/messages:send`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${fcmAccessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          message: {
            token,
            notification: { title, body },
            data: { event_type: event_type ?? "GENERAL", team_id },
          },
        }),
      },
    );
    if (resp.ok) sent++;
  }

  return new Response(JSON.stringify({ ok: true, fcm_sent: sent }), {
    headers: { "Content-Type": "application/json" },
  });
});
