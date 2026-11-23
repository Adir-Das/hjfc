-- ============================================================================
-- Strict Zero-Trust Row Level Security (RLS) Policies
-- Never trusts client-supplied roles; verifies role from public.team_members.
-- ============================================================================

CREATE OR REPLACE FUNCTION public.is_team_member(p_team_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM public.team_members tm
        WHERE tm.team_id = p_team_id
          AND tm.user_id = auth.uid()
          AND tm.registration_status = 'APPROVED'
          AND tm.status IN ('ACTIVE', 'INACTIVE')
    );
$$;

CREATE OR REPLACE FUNCTION public.current_team_role(p_team_id UUID)
RETURNS TEXT
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT tm.role
    FROM public.team_members tm
    WHERE tm.team_id = p_team_id
      AND tm.user_id = auth.uid()
      AND tm.registration_status = 'APPROVED'
      AND tm.status = 'ACTIVE'
    LIMIT 1;
$$;

CREATE OR REPLACE FUNCTION public.is_super_admin(p_team_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT public.current_team_role(p_team_id) = 'SUPER_ADMIN';
$$;

CREATE OR REPLACE FUNCTION public.is_treasurer_or_admin(p_team_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT public.current_team_role(p_team_id) IN ('SUPER_ADMIN', 'TREASURER');
$$;

-- Enable RLS on all tables
ALTER TABLE public.teams ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.team_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.team_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.games ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.availability ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.attendance ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.leave_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.contribution_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.member_dues ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.fund_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.fund_transfers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.expense_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.budgets ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.fund_goals ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.announcements ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.announcement_reads ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.team_polls ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.poll_options ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.poll_votes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.gallery_albums ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.team_gallery ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.match_results ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.player_match_statistics ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.equipment ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.equipment_assignments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.jerseys ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.whatsapp_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.scheduled_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.monthly_reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;

-- Users
CREATE POLICY "users_select_self_or_teammate" ON public.users
    FOR SELECT USING (
        id = auth.uid() OR EXISTS (
            SELECT 1 FROM public.team_members tm1
            JOIN public.team_members tm2 ON tm1.team_id = tm2.team_id
            WHERE tm1.user_id = auth.uid() AND tm2.user_id = users.id
        )
    );
CREATE POLICY "users_insert_self" ON public.users FOR INSERT WITH CHECK (id = auth.uid());
CREATE POLICY "users_update_self" ON public.users FOR UPDATE USING (id = auth.uid());

-- Teams
CREATE POLICY "teams_select_authorized" ON public.teams
    FOR SELECT USING (public.is_team_member(id) OR created_by = auth.uid());
CREATE POLICY "teams_insert_authenticated" ON public.teams
    FOR INSERT WITH CHECK (auth.uid() IS NOT NULL);
CREATE POLICY "teams_update_admin" ON public.teams
    FOR UPDATE USING (public.is_super_admin(id));

-- Team Members
CREATE POLICY "team_members_select_team" ON public.team_members
    FOR SELECT USING (public.is_team_member(team_id) OR user_id = auth.uid());
CREATE POLICY "team_members_insert_admin_or_self_pending" ON public.team_members
    FOR INSERT WITH CHECK (
        public.is_super_admin(team_id) OR (user_id = auth.uid() AND role = 'MEMBER' AND registration_status = 'PENDING')
    );
CREATE POLICY "team_members_update_admin_or_own_profile" ON public.team_members
    FOR UPDATE USING (
        public.is_super_admin(team_id) OR user_id = auth.uid()
    );

-- Team Settings
CREATE POLICY "team_settings_select" ON public.team_settings FOR SELECT USING (public.is_team_member(team_id));
CREATE POLICY "team_settings_manage_admin" ON public.team_settings FOR ALL USING (public.is_super_admin(team_id));

-- Games (Super Admin ONLY writes)
CREATE POLICY "games_select_team" ON public.games FOR SELECT USING (public.is_team_member(team_id));
CREATE POLICY "games_manage_admin" ON public.games FOR ALL USING (public.is_super_admin(team_id));

-- Availability (Respects team privacy setting & self/admin ownership)
CREATE POLICY "availability_select" ON public.availability
    FOR SELECT USING (
        public.is_super_admin(team_id)
        OR EXISTS (SELECT 1 FROM public.team_members tm WHERE tm.id = member_id AND tm.user_id = auth.uid())
        OR (
            public.is_team_member(team_id)
            AND COALESCE((SELECT ts.allow_members_view_others_availability FROM public.team_settings ts WHERE ts.team_id = availability.team_id), TRUE)
        )
    );
CREATE POLICY "availability_upsert_self_or_admin" ON public.availability
    FOR ALL USING (
        public.is_super_admin(team_id)
        OR EXISTS (SELECT 1 FROM public.team_members tm WHERE tm.id = member_id AND tm.user_id = auth.uid())
    );

-- Attendance (Super Admin & Treasurer manage; members view own or team stats)
CREATE POLICY "attendance_select" ON public.attendance
    FOR SELECT USING (
        public.is_treasurer_or_admin(team_id)
        OR EXISTS (SELECT 1 FROM public.team_members tm WHERE tm.id = member_id AND tm.user_id = auth.uid())
    );
CREATE POLICY "attendance_manage" ON public.attendance FOR ALL USING (public.is_treasurer_or_admin(team_id));

-- Leave Requests
CREATE POLICY "leave_select" ON public.leave_requests
    FOR SELECT USING (
        public.is_super_admin(team_id)
        OR EXISTS (SELECT 1 FROM public.team_members tm WHERE tm.id = member_id AND tm.user_id = auth.uid())
    );
CREATE POLICY "leave_insert_self" ON public.leave_requests
    FOR INSERT WITH CHECK (
        EXISTS (SELECT 1 FROM public.team_members tm WHERE tm.id = member_id AND tm.user_id = auth.uid() AND tm.team_id = leave_requests.team_id)
    );
CREATE POLICY "leave_update_admin" ON public.leave_requests FOR UPDATE USING (public.is_super_admin(team_id));

-- Contribution Rules & Categories
CREATE POLICY "contribution_rules_select" ON public.contribution_rules FOR SELECT USING (public.is_team_member(team_id));
CREATE POLICY "contribution_rules_admin" ON public.contribution_rules FOR ALL USING (public.is_super_admin(team_id));
CREATE POLICY "categories_select" ON public.categories FOR SELECT USING (public.is_team_member(team_id));
CREATE POLICY "categories_admin" ON public.categories FOR ALL USING (public.is_super_admin(team_id));

-- Member Dues (Members read ONLY their own; Treasurer & Admin read all; mutations via RPC/Admin)
CREATE POLICY "member_dues_select" ON public.member_dues
    FOR SELECT USING (
        public.is_treasurer_or_admin(team_id)
        OR EXISTS (SELECT 1 FROM public.team_members tm WHERE tm.id = member_id AND tm.user_id = auth.uid())
    );
CREATE POLICY "member_dues_manage" ON public.member_dues FOR ALL USING (public.is_treasurer_or_admin(team_id));

-- Fund Accounts & Transfers
CREATE POLICY "fund_accounts_select" ON public.fund_accounts FOR SELECT USING (public.is_team_member(team_id));
CREATE POLICY "fund_accounts_admin" ON public.fund_accounts FOR ALL USING (public.is_super_admin(team_id));
CREATE POLICY "fund_transfers_select" ON public.fund_transfers FOR SELECT USING (public.is_treasurer_or_admin(team_id));
CREATE POLICY "fund_transfers_manage" ON public.fund_transfers FOR INSERT WITH CHECK (public.is_treasurer_or_admin(team_id));

-- Transactions (Members read only their own contribution transactions; Treasurer/Admin manage within rules)
CREATE POLICY "transactions_select" ON public.transactions
    FOR SELECT USING (
        public.is_treasurer_or_admin(team_id)
        OR EXISTS (SELECT 1 FROM public.team_members tm WHERE tm.id = related_member_id AND tm.user_id = auth.uid())
    );
CREATE POLICY "transactions_insert_treasurer_or_admin" ON public.transactions
    FOR INSERT WITH CHECK (public.is_treasurer_or_admin(team_id));
CREATE POLICY "transactions_update_admin" ON public.transactions
    FOR UPDATE USING (public.is_super_admin(team_id));

-- Expense Requests
CREATE POLICY "expense_requests_select" ON public.expense_requests FOR SELECT USING (public.is_treasurer_or_admin(team_id));
CREATE POLICY "expense_requests_insert_treasurer_or_admin" ON public.expense_requests
    FOR INSERT WITH CHECK (public.is_treasurer_or_admin(team_id));
CREATE POLICY "expense_requests_update_admin" ON public.expense_requests
    FOR UPDATE USING (public.is_super_admin(team_id));

-- Budgets, Fund Goals, Monthly Reports
CREATE POLICY "budgets_select" ON public.budgets FOR SELECT USING (public.is_team_member(team_id));
CREATE POLICY "budgets_admin" ON public.budgets FOR ALL USING (public.is_super_admin(team_id));
CREATE POLICY "fund_goals_select" ON public.fund_goals FOR SELECT USING (public.is_team_member(team_id));
CREATE POLICY "fund_goals_admin" ON public.fund_goals FOR ALL USING (public.is_super_admin(team_id));
CREATE POLICY "monthly_reports_select" ON public.monthly_reports FOR SELECT USING (public.is_team_member(team_id));
CREATE POLICY "monthly_reports_manage" ON public.monthly_reports FOR ALL USING (public.is_treasurer_or_admin(team_id));

-- Notifications
CREATE POLICY "notifications_select_own" ON public.notifications
    FOR SELECT USING (user_id = auth.uid() OR (user_id IS NULL AND public.is_team_member(team_id)));
CREATE POLICY "notifications_update_own" ON public.notifications FOR UPDATE USING (user_id = auth.uid());
CREATE POLICY "notifications_insert_admin" ON public.notifications FOR INSERT WITH CHECK (public.is_treasurer_or_admin(team_id));

-- Announcements & Polls
CREATE POLICY "announcements_select" ON public.announcements FOR SELECT USING (public.is_team_member(team_id));
CREATE POLICY "announcements_admin" ON public.announcements FOR ALL USING (public.is_super_admin(team_id));
CREATE POLICY "announcement_reads_all" ON public.announcement_reads FOR ALL USING (public.is_team_member(team_id));
CREATE POLICY "team_polls_select" ON public.team_polls FOR SELECT USING (public.is_team_member(team_id));
CREATE POLICY "team_polls_admin" ON public.team_polls FOR ALL USING (public.is_super_admin(team_id));
CREATE POLICY "poll_options_select" ON public.poll_options FOR SELECT USING (public.is_team_member(team_id));
CREATE POLICY "poll_options_admin" ON public.poll_options FOR ALL USING (public.is_super_admin(team_id));
CREATE POLICY "poll_votes_select" ON public.poll_votes FOR SELECT USING (public.is_team_member(team_id));
CREATE POLICY "poll_votes_insert_self" ON public.poll_votes
    FOR INSERT WITH CHECK (
        EXISTS (SELECT 1 FROM public.team_members tm WHERE tm.id = member_id AND tm.user_id = auth.uid() AND tm.team_id = poll_votes.team_id)
    );

-- Gallery, Match Results, Stats, Equipment, Jerseys
CREATE POLICY "gallery_albums_select" ON public.gallery_albums FOR SELECT USING (public.is_team_member(team_id));
CREATE POLICY "gallery_albums_admin" ON public.gallery_albums FOR ALL USING (public.is_super_admin(team_id));
CREATE POLICY "team_gallery_select" ON public.team_gallery FOR SELECT USING (public.is_team_member(team_id));
CREATE POLICY "team_gallery_admin" ON public.team_gallery FOR ALL USING (public.is_super_admin(team_id));
CREATE POLICY "match_results_select" ON public.match_results FOR SELECT USING (public.is_team_member(team_id));
CREATE POLICY "match_results_admin" ON public.match_results FOR ALL USING (public.is_super_admin(team_id));
CREATE POLICY "player_stats_select" ON public.player_match_statistics FOR SELECT USING (public.is_team_member(team_id));
CREATE POLICY "player_stats_admin" ON public.player_match_statistics FOR ALL USING (public.is_super_admin(team_id));
CREATE POLICY "equipment_select" ON public.equipment FOR SELECT USING (public.is_team_member(team_id));
CREATE POLICY "equipment_admin" ON public.equipment FOR ALL USING (public.is_super_admin(team_id));
CREATE POLICY "equipment_assignments_select" ON public.equipment_assignments FOR SELECT USING (public.is_team_member(team_id));
CREATE POLICY "equipment_assignments_admin" ON public.equipment_assignments FOR ALL USING (public.is_super_admin(team_id));
CREATE POLICY "jerseys_select" ON public.jerseys FOR SELECT USING (public.is_team_member(team_id));
CREATE POLICY "jerseys_admin" ON public.jerseys FOR ALL USING (public.is_super_admin(team_id));

-- WhatsApp Logs & Scheduled Jobs
CREATE POLICY "whatsapp_logs_admin" ON public.whatsapp_logs FOR ALL USING (public.is_super_admin(team_id));
CREATE POLICY "scheduled_jobs_admin" ON public.scheduled_jobs FOR SELECT USING (public.is_super_admin(team_id));

-- Audit Logs (Immutable: Super Admin SELECT only; NO UPDATE/DELETE)
CREATE POLICY "audit_logs_select_admin" ON public.audit_logs FOR SELECT USING (public.is_super_admin(team_id));
CREATE POLICY "audit_logs_insert_system" ON public.audit_logs FOR INSERT WITH CHECK (public.is_team_member(team_id));
