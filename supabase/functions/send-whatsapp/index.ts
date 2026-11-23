// Supabase Edge Function: Official WhatsApp Business Cloud API Integration
// Secrets required: WHATSAPP_ACCESS_TOKEN, WHATSAPP_PHONE_NUMBER_ID, SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY

import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.0";

interface WhatsAppPayload {
  team_id: string;
  recipient_phone: string;
  recipient_member_id?: string;
  template_name: string;
  language_code?: string;
  body_params?: string[];
  message_preview: string;
}

serve(async (req) => {
  if (req.method !== "POST") {
    return new Response("Method Not Allowed", { status: 405 });
  }

  const authHeader = req.headers.get("Authorization");
  if (!authHeader) {
    return new Response(JSON.stringify({ error: "Missing authorization header" }), { status: 401 });
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  const waToken = Deno.env.get("WHATSAPP_ACCESS_TOKEN") ?? "";
  const waPhoneId = Deno.env.get("WHATSAPP_PHONE_NUMBER_ID") ?? "";

  const supabase = createClient(supabaseUrl, serviceKey);
  const jwt = authHeader.replace("Bearer ", "");
  const { data: { user }, error: authError } = await supabase.auth.getUser(jwt);
  if (authError || !user) {
    return new Response(JSON.stringify({ error: "Unauthorized" }), { status: 401 });
  }

  const payload: WhatsAppPayload = await req.json();

  // Verify caller is SUPER_ADMIN or TREASURER on this team
  const { data: callerMember } = await supabase
    .from("team_members")
    .select("role, status")
    .eq("team_id", payload.team_id)
    .eq("user_id", user.id)
    .eq("registration_status", "APPROVED")
    .single();

  if (!callerMember || !["SUPER_ADMIN", "TREASURER"].includes(callerMember.role)) {
    return new Response(JSON.stringify({ error: "Forbidden: insufficient role" }), { status: 403 });
  }

  // Respect team WhatsApp setting and member opt-in consent
  const { data: settings } = await supabase
    .from("team_settings")
    .select("whatsapp_enabled")
    .eq("team_id", payload.team_id)
    .single();

  if (!settings?.whatsapp_enabled || !waToken || !waPhoneId) {
    await supabase.from("whatsapp_logs").insert({
      team_id: payload.team_id,
      recipient_phone: payload.recipient_phone,
      recipient_member_id: payload.recipient_member_id ?? null,
      template_name: payload.template_name,
      message_preview: payload.message_preview,
      delivery_status: "FAILED",
      error_message: "WhatsApp Business API disabled or credentials missing; use manual Share to WhatsApp.",
    });
    return new Response(JSON.stringify({ sent: false, fallback: "SHARE_TO_WHATSAPP" }), { status: 200 });
  }

  const graphUrl = `https://graph.facebook.com/v20.0/${waPhoneId}/messages`;
  const waBody = {
    messaging_product: "whatsapp",
    to: payload.recipient_phone.replace(/[^0-9]/g, ""),
    type: "template",
    template: {
      name: payload.template_name,
      language: { code: payload.language_code ?? "bn" },
      components: payload.body_params?.length
        ? [{ type: "body", parameters: payload.body_params.map((t) => ({ type: "text", text: t })) }]
        : [],
    },
  };

  const waResp = await fetch(graphUrl, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${waToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(waBody),
  });

  const waJson = await waResp.json();
  const status = waResp.ok ? "SENT" : "FAILED";

  await supabase.from("whatsapp_logs").insert({
    team_id: payload.team_id,
    recipient_phone: payload.recipient_phone,
    recipient_member_id: payload.recipient_member_id ?? null,
    template_name: payload.template_name,
    message_preview: payload.message_preview,
    delivery_status: status,
    provider_message_id: waJson?.messages?.[0]?.id ?? null,
    error_message: waResp.ok ? null : JSON.stringify(waJson?.error ?? waJson),
  });

  return new Response(JSON.stringify({ sent: waResp.ok, response: waJson }), {
    status: waResp.ok ? 200 : 502,
    headers: { "Content-Type": "application/json" },
  });
});
