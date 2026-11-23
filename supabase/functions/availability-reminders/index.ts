// Supabase Edge Function: Automatic Availability Reminders (Scheduled via pg_cron)
// Uses Asia/Dhaka timezone and prevents duplicate reminders via scheduled_jobs.

import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.0";

serve(async (_req) => {
  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  const supabase = createClient(supabaseUrl, serviceKey);

  const now = new Date();
  const { data: upcomingGames } = await supabase
    .from("games")
    .select("id, team_id, title, opponent_name, match_date, match_time, venue_name, availability_deadline")
    .eq("status", "UPCOMING")
    .gte("match_date", now.toISOString().slice(0, 10));

  let dispatchedCount = 0;

  for (const game of upcomingGames ?? []) {
    const deadline = game.availability_deadline
      ? new Date(game.availability_deadline)
      : new Date(`${game.match_date}T18:00:00+06:00`);
    const hoursUntilDeadline = (deadline.getTime() - now.getTime()) / 3600000;

    let stage = "";
    if (hoursUntilDeadline > 6 && hoursUntilDeadline <= 22) stage = "FIRST_REMINDER";
    else if (hoursUntilDeadline > 0 && hoursUntilDeadline <= 6) stage = "FINAL_REMINDER";
    else continue;

    const jobKey = `AVAIL_REMINDER_${game.id}_${stage}`;
    const { data: existingJob } = await supabase
      .from("scheduled_jobs")
      .select("id")
      .eq("job_key", jobKey)
      .maybeSingle();

    if (existingJob) continue; // Duplicate reminder prevented

    const { data: activeMembers } = await supabase
      .from("team_members")
      .select("id, user_id, full_name, whatsapp_number, whatsapp_opt_in")
      .eq("team_id", game.team_id)
      .eq("status", "ACTIVE")
      .eq("registration_status", "APPROVED");

    const { data: responded } = await supabase
      .from("availability")
      .select("member_id, status")
      .eq("team_id", game.team_id)
      .eq("avail_date", game.match_date)
      .in("status", ["AVAILABLE", "UNAVAILABLE"]);

    const respondedSet = new Set((responded ?? []).map((r) => r.member_id));

    for (const member of activeMembers ?? []) {
      if (respondedSet.has(member.id)) continue;

      if (member.user_id) {
        await supabase.from("notifications").insert({
          team_id: game.team_id,
          user_id: member.user_id,
          event_type: "AVAILABILITY_REMINDER",
          title: `উপস্থিতি অনুস্মারক: ${game.title}`,
          body: `${game.match_date} তারিখে ${game.opponent_name}-এর বিপক্ষে ম্যাচের জন্য অনুগ্রহ করে আপনার অ্যাভেইলেবিলিটি জানান।`,
          reference_id: game.id,
        });
        dispatchedCount++;
      }
    }

    await supabase.from("scheduled_jobs").insert({
      team_id: game.team_id,
      job_key: jobKey,
      job_type: stage,
      status: "COMPLETED",
    });
  }

  return new Response(JSON.stringify({ ok: true, reminders_created: dispatchedCount }), {
    headers: { "Content-Type": "application/json" },
  });
});
