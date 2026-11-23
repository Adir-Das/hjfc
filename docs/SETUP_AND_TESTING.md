# Journalist Sports Club Management — Setup, Deployment & QA Guide

## 1. Environment Configuration
1. Open the **Secrets** panel in Google AI Studio (or create a local `.env` file for local Android Studio builds — never commit `.env`).
2. Set the following variables (matching `.env.example`):
   - `SUPABASE_URL`: Your Supabase project URL (`https://<project-ref>.supabase.co`).
   - `SUPABASE_ANON_KEY`: Your Supabase project `anon` public key.
3. For Firebase Cloud Messaging (FCM):
   - Download `google-services.json` from Firebase Console for package `com.aistudio.journalistclub.kxmpzq` and place it under `app/google-services.json`.

## 2. Supabase Database & Storage Setup
Run the SQL migration scripts in order inside your Supabase SQL Editor:
1. `supabase/migrations/001_schema.sql` (34 normalized tables, constraints, indexes; no match-date polling tables).
2. `supabase/migrations/002_rls_policies.sql` (zero-trust Row Level Security policies).
3. `supabase/migrations/003_functions_and_triggers.sql` (atomic financial RPCs & audit triggers).
4. Create three Supabase Storage buckets (`avatars`, `receipts`, `team-gallery`) with authenticated team-scoped read/write policies.
5. Enable Supabase Realtime replication on `games`, `availability`, `attendance`, `transactions`, `expense_requests`, `announcements`, `team_polls`, `poll_votes`, and `notifications`.

## 3. Supabase Edge Functions & WhatsApp Automation
Deploy Edge Functions via Supabase CLI:
```bash
supabase functions deploy send-whatsapp
supabase functions deploy availability-reminders
supabase functions deploy send-fcm-notification
```
Configure Edge Function secrets:
- `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID` (Meta WhatsApp Business Cloud API v20.0)
- `FIREBASE_PROJECT_ID`, `FIREBASE_ACCESS_TOKEN`
Schedule `availability-reminders` hourly using Supabase `pg_cron` (`Asia/Dhaka` match deadlines).
If WhatsApp Business API templates are pending Meta verification, administrators can use the built-in **Share to WhatsApp** workflow from Finance, Matches, and Announcements.

## 4. Admin Setup Instructions
1. Launch the app and register the first user on the **Create Club / Team** tab.
2. `bootstrap_team` automatically assigns `SUPER_ADMIN`, creates default BDT (`৳`) Cash/Bank/MFS accounts, income/expense categories, and generates a unique **Team Invite Code**.
3. Share the Invite Code with fellow journalists. They register using **Join with Invite Code**, and Super Admin approves pending registrations in **Members -> Pending Approvals**.
4. Super Admin can assign/remove `TREASURER` permissions, configure contribution rules (fixed per match, attendance-based, monthly, discounts), and set availability deadlines.

## 5. Testing & QA Checklist
Create three test accounts (`superadmin@jsc.club`, `treasurer@jsc.club`, `member@jsc.club`):
- **Security & RLS**: Verify Member cannot insert/edit `transactions`, approve `expense_requests`, or create `games`. Verify `audit_logs` cannot be modified or deleted.
- **Availability vs Attendance**: Submit member availability (`Available`/`Unavailable`) and confirm `member_dues` does not change until confirmed `attendance` (`Present`/`Late`) is recorded.
- **Financial Integrity**: Test partial payments, advance payments (applied once without double-counting), Treasurer expense submission (pending does not reduce cash balance), Super Admin approval (reduces account balance and creates approved transaction), and balanced fund transfers.
- **Exports & Localization**: Switch language between Bengali (default) and English, generate a Monthly PDF (verifying Bengali `Noto Sans Bengali` glyph rendering + JSC emblem) and Excel `.xls` export.
