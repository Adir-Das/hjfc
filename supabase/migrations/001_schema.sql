-- ============================================================================
-- Journalist Sports Club Management - PostgreSQL / Supabase Schema
-- Currency: BDT (৳) | Default Timezone: Asia/Dhaka
-- Note: Match date polling is intentionally omitted per business rules.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. Teams
CREATE TABLE IF NOT EXISTS public.teams (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    short_name TEXT NOT NULL DEFAULT 'JSC',
    invite_code TEXT NOT NULL UNIQUE DEFAULT upper(substr(encode(gen_random_bytes(5), 'hex'), 1, 8)),
    timezone TEXT NOT NULL DEFAULT 'Asia/Dhaka',
    currency_code TEXT NOT NULL DEFAULT 'BDT',
    currency_symbol TEXT NOT NULL DEFAULT '৳',
    logo_url TEXT,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 2. Users (App profile linked to auth.users)
CREATE TABLE IF NOT EXISTS public.users (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email TEXT NOT NULL UNIQUE,
    full_name TEXT NOT NULL,
    phone TEXT,
    whatsapp_number TEXT,
    avatar_url TEXT,
    preferred_language TEXT NOT NULL DEFAULT 'bn' CHECK (preferred_language IN ('bn', 'en')),
    fcm_token TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 3. Team Members
CREATE TABLE IF NOT EXISTS public.team_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES public.teams(id) ON DELETE CASCADE,
    user_id UUID REFERENCES public.users(id) ON DELETE SET NULL,
    member_code TEXT NOT NULL,
    full_name TEXT NOT NULL,
    email TEXT NOT NULL,
    mobile_number TEXT NOT NULL DEFAULT '',
    whatsapp_number TEXT NOT NULL DEFAULT '',
    profile_picture_url TEXT,
    date_of_birth DATE,
    joining_date DATE NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Dhaka')::DATE,
    jersey_size TEXT NOT NULL DEFAULT 'L',
    preferred_position TEXT NOT NULL DEFAULT 'Midfielder',
    emergency_contact TEXT,
    role TEXT NOT NULL DEFAULT 'MEMBER' CHECK (role IN ('SUPER_ADMIN', 'TREASURER', 'MEMBER')),
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'FORMER_MEMBER')),
    registration_status TEXT NOT NULL DEFAULT 'APPROVED' CHECK (registration_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    whatsapp_opt_in BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (team_id, email),
    UNIQUE (team_id, member_code)
);
CREATE INDEX idx_team_members_team ON public.team_members(team_id);
CREATE INDEX idx_team_members_user ON public.team_members(user_id);

-- 4. Team Settings
CREATE TABLE IF NOT EXISTS public.team_settings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL UNIQUE REFERENCES public.teams(id) ON DELETE CASCADE,
    availability_deadline_hours_before INTEGER NOT NULL DEFAULT 18,
    first_reminder_hours_before INTEGER NOT NULL DEFAULT 40,
    final_reminder_hours_before INTEGER NOT NULL DEFAULT 24,
    allow_members_view_others_availability BOOLEAN NOT NULL DEFAULT TRUE,
    allow_member_availability_edit_before_deadline BOOLEAN NOT NULL DEFAULT TRUE,
    expense_approval_limit_bdt NUMERIC(14, 2) NOT NULL DEFAULT 5000.00,
    whatsapp_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    whatsapp_daily_report_time TEXT NOT NULL DEFAULT '21:00',
    default_language TEXT NOT NULL DEFAULT 'bn' CHECK (default_language IN ('bn', 'en')),
    updated_by UUID REFERENCES public.users(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 5. Games / Matches (Controlled exclusively by Super Admin; NO date polling)
CREATE TABLE IF NOT EXISTS public.games (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES public.teams(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    opponent_name TEXT NOT NULL,
    match_date DATE NOT NULL,
    match_time TIME NOT NULL,
    venue_name TEXT NOT NULL,
    maps_url TEXT,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    description TEXT,
    expected_budget NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    availability_deadline TIMESTAMPTZ,
    status TEXT NOT NULL DEFAULT 'UPCOMING' CHECK (status IN ('UPCOMING', 'COMPLETED', 'POSTPONED', 'CANCELLED')),
    is_published BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID NOT NULL REFERENCES public.users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_games_team_date ON public.games(team_id, match_date DESC);

-- 6. Member Availability (Separate from attendance!)
CREATE TABLE IF NOT EXISTS public.availability (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES public.teams(id) ON DELETE CASCADE,
    member_id UUID NOT NULL REFERENCES public.team_members(id) ON DELETE CASCADE,
    avail_date DATE NOT NULL,
    game_id UUID REFERENCES public.games(id) ON DELETE SET NULL,
    status TEXT NOT NULL DEFAULT 'NOT_RESPONDED' CHECK (status IN ('AVAILABLE', 'UNAVAILABLE', 'NOT_RESPONDED')),
    remarks TEXT,
    overridden_by UUID REFERENCES public.users(id),
    updated_by UUID REFERENCES public.users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (team_id, member_id, avail_date)
);
CREATE INDEX idx_availability_team_date ON public.availability(team_id, avail_date);

-- 7. Attendance (Confirmed final attendance, separate from availability)
CREATE TABLE IF NOT EXISTS public.attendance (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES public.teams(id) ON DELETE CASCADE,
    game_id UUID NOT NULL REFERENCES public.games(id) ON DELETE CASCADE,
    member_id UUID NOT NULL REFERENCES public.team_members(id) ON DELETE CASCADE,
    status TEXT NOT NULL CHECK (status IN ('PRESENT', 'ABSENT', 'LATE', 'EXCUSED')),
    absence_reason TEXT,
    recorded_by UUID NOT NULL REFERENCES public.users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (game_id, member_id)
);
CREATE INDEX idx_attendance_team_game ON public.attendance(team_id, game_id);

-- 8. Leave Requests
CREATE TABLE IF NOT EXISTS public.leave_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES public.teams(id) ON DELETE CASCADE,
    member_id UUID NOT NULL REFERENCES public.team_members(id) ON DELETE CASCADE,
    leave_date DATE NOT NULL,
    reason TEXT NOT NULL,
    attachment_url TEXT,
    additional_note TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'INFO_REQUESTED')),
    admin_remarks TEXT,
    reviewed_by UUID REFERENCES public.users(id),
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_leave_requests_team_status ON public.leave_requests(team_id, status);

-- 9. Contribution Rules
CREATE TABLE IF NOT EXISTS public.contribution_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES public.teams(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    rule_type TEXT NOT NULL CHECK (rule_type IN (
        'FIXED_PER_MATCH', 'MONTHLY_FIXED', 'CUSTOM_PER_MATCH',
        'ATTENDANCE_BASED', 'EVEN_WHEN_ABSENT', 'SPECIAL', 'MEMBER_DISCOUNT', 'ADVANCE'
    )),
    amount NUMERIC(14, 2) NOT NULL CHECK (amount >= 0),
    game_id UUID REFERENCES public.games(id) ON DELETE SET NULL,
    target_member_id UUID REFERENCES public.team_members(id) ON DELETE SET NULL,
    effective_from DATE NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Dhaka')::DATE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID REFERENCES public.users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 10. Member Dues (Server-calculated ledger summary per member)
CREATE TABLE IF NOT EXISTS public.member_dues (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES public.teams(id) ON DELETE CASCADE,
    member_id UUID NOT NULL REFERENCES public.team_members(id) ON DELETE CASCADE,
    total_payable NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    total_paid NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    applied_advance NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    advance_balance NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    discount_amount NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    additional_charges NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    outstanding_balance NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    last_calculated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (team_id, member_id)
);

-- 11. Categories (Income & Expense)
CREATE TABLE IF NOT EXISTS public.categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES public.teams(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    name_bn TEXT NOT NULL DEFAULT '',
    type TEXT NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (team_id, name, type)
);

-- 12. Fund Accounts (Cash, Bank, MFS)
CREATE TABLE IF NOT EXISTS public.fund_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES public.teams(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    account_type TEXT NOT NULL CHECK (account_type IN ('CASH', 'BANK', 'MFS')),
    account_number TEXT,
    opening_balance NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    current_balance NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 13. Transactions (Approved financial records affect official statements)
CREATE TABLE IF NOT EXISTS public.transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES public.teams(id) ON DELETE CASCADE,
    transaction_ref TEXT NOT NULL UNIQUE,
    type TEXT NOT NULL CHECK (type IN ('INCOME', 'EXPENSE', 'TRANSFER')),
    category TEXT NOT NULL,
    amount NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    txn_date DATE NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Dhaka')::DATE,
    description TEXT NOT NULL,
    related_member_id UUID REFERENCES public.team_members(id) ON DELETE SET NULL,
    related_game_id UUID REFERENCES public.games(id) ON DELETE SET NULL,
    fund_account_id UUID REFERENCES public.fund_accounts(id) ON DELETE SET NULL,
    payment_method TEXT NOT NULL DEFAULT 'CASH' CHECK (payment_method IN ('CASH', 'BANK', 'BKASH', 'NAGAD', 'ROCKET', 'OTHER')),
    receipt_url TEXT,
    is_advance BOOLEAN NOT NULL DEFAULT FALSE,
    approval_status TEXT NOT NULL DEFAULT 'APPROVED' CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    created_by UUID NOT NULL REFERENCES public.users(id),
    approved_by UUID REFERENCES public.users(id),
    cancelled_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_transactions_team_date ON public.transactions(team_id, txn_date DESC);

-- 14. Fund Transfers (Balanced Debit & Credit)
CREATE TABLE IF NOT EXISTS public.fund_transfers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES public.teams(id) ON DELETE CASCADE,
    from_account_id UUID NOT NULL REFERENCES public.fund_accounts(id),
    to_account_id UUID NOT NULL REFERENCES public.fund_accounts(id),
    amount NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    transfer_date DATE NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Dhaka')::DATE,
    reference_code TEXT NOT NULL UNIQUE,
    notes TEXT,
    created_by UUID NOT NULL REFERENCES public.users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (from_account_id <> to_account_id)
);

-- 15. Expense Requests (Submitted by Treasurer, approved by Super Admin)
CREATE TABLE IF NOT EXISTS public.expense_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES public.teams(id) ON DELETE CASCADE,
    requested_amount NUMERIC(14, 2) NOT NULL CHECK (requested_amount > 0),
    category TEXT NOT NULL,
    description TEXT NOT NULL,
    related_game_id UUID REFERENCES public.games(id) ON DELETE SET NULL,
    fund_account_id UUID REFERENCES public.fund_accounts(id) ON DELETE SET NULL,
    receipt_url TEXT,
    submission_date DATE NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Dhaka')::DATE,
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'MODIFICATION_REQUESTED')),
    approval_remarks TEXT,
    requested_by UUID NOT NULL REFERENCES public.users(id),
    reviewed_by UUID REFERENCES public.users(id),
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 16. Budgets
CREATE TABLE IF NOT EXISTS public.budgets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES public.teams(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    budget_type TEXT NOT NULL CHECK (budget_type IN ('MONTHLY', 'MATCH', 'ANNUAL')),
    period_key TEXT NOT NULL,
    game_id UUID REFERENCES public.games(id) ON DELETE SET NULL,
    budget_amount NUMERIC(14, 2) NOT NULL CHECK (budget_amount >= 0),
    created_by UUID REFERENCES public.users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 17. Fund Goals
CREATE TABLE IF NOT EXISTS public.fund_goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES public.teams(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    goal_type TEXT NOT NULL CHECK (goal_type IN ('CONTRIBUTION_TARGET', 'SPECIAL_FUNDRAISING')),
    target_amount NUMERIC(14, 2) NOT NULL CHECK (target_amount > 0),
    collected_amount NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    deadline DATE,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 18. Notifications
CREATE TABLE IF NOT EXISTS public.notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES public.teams(id) ON DELETE CASCADE,
    user_id UUID REFERENCES public.users(id) ON DELETE CASCADE,
    event_type TEXT NOT NULL,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    reference_id UUID,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user ON public.notifications(user_id, created_at DESC);

-- 19. Announcements & 20. Announcement Reads
CREATE TABLE IF NOT EXISTS public.announcements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES public.teams(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMPTZ,
    attachment_url TEXT,
    target_member_ids UUID[],
    created_by UUID NOT NULL REFERENCES public.users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.announcement_reads (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES public.teams(id) ON DELETE CASCADE,
    announcement_id UUID NOT NULL REFERENCES public.announcements(id) ON DELETE CASCADE,
    member_id UUID NOT NULL REFERENCES public.team_members(id) ON DELETE CASCADE,
    read_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (announcement_id, member_id)
);

-- 21. Team Polls, 22. Poll Options, 23. Poll Votes (General & Man of the Match ONLY — NO match date polls!)
CREATE TABLE IF NOT EXISTS public.team_polls (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES public.teams(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    description TEXT,
    poll_category TEXT NOT NULL DEFAULT 'GENERAL' CHECK (poll_category IN ('TEAM_DECISION', 'JERSEY_SELECTION', 'MAN_OF_THE_MATCH', 'GENERAL')),
    related_game_id UUID REFERENCES public.games(id) ON DELETE SET NULL,
    is_anonymous BOOLEAN NOT NULL DEFAULT FALSE,
    deadline TIMESTAMPTZ NOT NULL,
    is_closed BOOLEAN NOT NULL DEFAULT FALSE,
    created_by UUID NOT NULL REFERENCES public.users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.poll_options (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES public.teams(id) ON DELETE CASCADE,
    poll_id UUID NOT NULL REFERENCES public.team_polls(id) ON DELETE CASCADE,
    option_text TEXT NOT NULL,
    linked_member_id UUID REFERENCES public.team_members(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS public.poll_votes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES public.teams(id) ON DELETE CASCADE,
    poll_id UUID NOT NULL REFERENCES public.team_polls(id) ON DELETE CASCADE,
    option_id UUID NOT NULL REFERENCES public.poll_options(id) ON DELETE CASCADE,
    member_id UUID NOT NULL REFERENCES public.team_members(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (poll_id, member_id)
);

-- 24. Gallery Albums & 25. Team Gallery
CREATE TABLE IF NOT EXISTS public.gallery_albums (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES public.teams(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    game_id UUID REFERENCES public.games(id) ON DELETE SET NULL,
    album_date DATE NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Dhaka')::DATE,
    created_by UUID NOT NULL REFERENCES public.users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.team_gallery (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES public.teams(id) ON DELETE CASCADE,
    album_id UUID REFERENCES public.gallery_albums(id) ON DELETE SET NULL,
    game_id UUID REFERENCES public.games(id) ON DELETE SET NULL,
    media_type TEXT NOT NULL CHECK (media_type IN ('IMAGE', 'VIDEO')),
    media_url TEXT NOT NULL,
    caption TEXT,
    is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    uploaded_by UUID NOT NULL REFERENCES public.users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 26. Match Results & 27. Player Match Statistics
CREATE TABLE IF NOT EXISTS public.match_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES public.teams(id) ON DELETE CASCADE,
    game_id UUID NOT NULL UNIQUE REFERENCES public.games(id) ON DELETE CASCADE,
    opponent_name TEXT NOT NULL,
    match_date DATE NOT NULL,
    team_score INTEGER NOT NULL DEFAULT 0 CHECK (team_score >= 0),
    opponent_score INTEGER NOT NULL DEFAULT 0 CHECK (opponent_score >= 0),
    outcome TEXT NOT NULL CHECK (outcome IN ('WIN', 'LOSS', 'DRAW')),
    man_of_the_match_member_id UUID REFERENCES public.team_members(id) ON DELETE SET NULL,
    motm_selection_method TEXT DEFAULT 'SUPER_ADMIN' CHECK (motm_selection_method IN ('SUPER_ADMIN', 'MEMBER_POLL')),
    match_notes TEXT,
    created_by UUID NOT NULL REFERENCES public.users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.player_match_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES public.teams(id) ON DELETE CASCADE,
    game_id UUID NOT NULL REFERENCES public.games(id) ON DELETE CASCADE,
    member_id UUID NOT NULL REFERENCES public.team_members(id) ON DELETE CASCADE,
    goals INTEGER NOT NULL DEFAULT 0 CHECK (goals >= 0),
    assists INTEGER NOT NULL DEFAULT 0 CHECK (assists >= 0),
    is_player_of_match BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (game_id, member_id)
);

-- 28. Equipment, 29. Equipment Assignments, 30. Jerseys
CREATE TABLE IF NOT EXISTS public.equipment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES public.teams(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    category TEXT NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity >= 0),
    available_quantity INTEGER NOT NULL DEFAULT 1 CHECK (available_quantity >= 0),
    low_stock_threshold INTEGER NOT NULL DEFAULT 2,
    purchase_date DATE,
    purchase_cost NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    condition TEXT NOT NULL DEFAULT 'GOOD' CHECK (condition IN ('NEW', 'GOOD', 'FAIR', 'DAMAGED', 'LOST')),
    storage_location TEXT NOT NULL DEFAULT 'Club Room',
    responsible_member_id UUID REFERENCES public.team_members(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.equipment_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES public.teams(id) ON DELETE CASCADE,
    equipment_id UUID NOT NULL REFERENCES public.equipment(id) ON DELETE CASCADE,
    member_id UUID NOT NULL REFERENCES public.team_members(id) ON DELETE CASCADE,
    quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
    checkout_date DATE NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Dhaka')::DATE,
    return_date DATE,
    status TEXT NOT NULL DEFAULT 'CHECKED_OUT' CHECK (status IN ('CHECKED_OUT', 'RETURNED', 'DAMAGED', 'LOST')),
    notes TEXT,
    created_by UUID REFERENCES public.users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.jerseys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES public.teams(id) ON DELETE CASCADE,
    jersey_number INTEGER NOT NULL CHECK (jersey_number >= 0),
    size TEXT NOT NULL DEFAULT 'L',
    assigned_member_id UUID REFERENCES public.team_members(id) ON DELETE SET NULL,
    issue_date DATE,
    return_date DATE,
    condition TEXT NOT NULL DEFAULT 'GOOD' CHECK (condition IN ('NEW', 'GOOD', 'FAIR', 'DAMAGED', 'LOST')),
    UNIQUE (team_id, jersey_number)
);

-- 31. WhatsApp Logs
CREATE TABLE IF NOT EXISTS public.whatsapp_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES public.teams(id) ON DELETE CASCADE,
    recipient_phone TEXT NOT NULL,
    recipient_member_id UUID REFERENCES public.team_members(id) ON DELETE SET NULL,
    template_name TEXT NOT NULL,
    message_preview TEXT NOT NULL,
    delivery_status TEXT NOT NULL DEFAULT 'QUEUED' CHECK (delivery_status IN ('QUEUED', 'SENT', 'DELIVERED', 'READ', 'FAILED', 'SHARED_MANUALLY')),
    provider_message_id TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 32. Scheduled Jobs (Prevents duplicate automated reminders)
CREATE TABLE IF NOT EXISTS public.scheduled_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES public.teams(id) ON DELETE CASCADE,
    job_key TEXT NOT NULL UNIQUE,
    job_type TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'COMPLETED',
    executed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 33. Monthly Reports (Preserves finalized monthly history)
CREATE TABLE IF NOT EXISTS public.monthly_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES public.teams(id) ON DELETE CASCADE,
    report_period TEXT NOT NULL,
    opening_balance NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    total_contributions NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    other_income NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    total_expenses NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    closing_balance NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    outstanding_dues NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    advance_payments NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    pdf_url TEXT,
    excel_url TEXT,
    generated_by UUID NOT NULL REFERENCES public.users(id),
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (team_id, report_period)
);

-- 34. Audit Logs (Immutable)
CREATE TABLE IF NOT EXISTS public.audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES public.teams(id) ON DELETE CASCADE,
    actor_user_id UUID REFERENCES public.users(id),
    action_type TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    previous_values JSONB,
    new_values JSONB,
    remarks TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_logs_team_time ON public.audit_logs(team_id, created_at DESC);
