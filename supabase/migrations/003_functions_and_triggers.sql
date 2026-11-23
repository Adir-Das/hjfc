-- ============================================================================
-- Transactional Financial & Business Logic Functions + Immutable Audit Triggers
-- Enforces server-side integrity and prevents race conditions via FOR UPDATE.
-- ============================================================================

-- 1. Bootstrap Team (First user becomes SUPER_ADMIN and initializes default accounts & categories)
CREATE OR REPLACE FUNCTION public.bootstrap_team(
    p_team_name TEXT,
    p_short_name TEXT,
    p_full_name TEXT,
    p_email TEXT,
    p_mobile TEXT DEFAULT ''
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id UUID := auth.uid();
    v_team_id UUID;
    v_member_id UUID;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    INSERT INTO public.users (id, email, full_name, phone, whatsapp_number)
    VALUES (v_user_id, p_email, p_full_name, p_mobile, p_mobile)
    ON CONFLICT (id) DO UPDATE SET full_name = EXCLUDED.full_name;

    INSERT INTO public.teams (name, short_name, created_by)
    VALUES (p_team_name, COALESCE(NULLIF(p_short_name, ''), 'JSC'), v_user_id)
    RETURNING id INTO v_team_id;

    INSERT INTO public.team_settings (team_id, updated_by)
    VALUES (v_team_id, v_user_id);

    INSERT INTO public.team_members (
        team_id, user_id, member_code, full_name, email, mobile_number, whatsapp_number, role, status, registration_status
    )
    VALUES (
        v_team_id, v_user_id, 'JSC-001', p_full_name, p_email, p_mobile, p_mobile, 'SUPER_ADMIN', 'ACTIVE', 'APPROVED'
    )
    RETURNING id INTO v_member_id;

    INSERT INTO public.member_dues (team_id, member_id) VALUES (v_team_id, v_member_id);

    INSERT INTO public.fund_accounts (team_id, name, account_type) VALUES
        (v_team_id, 'Club Cash Box', 'CASH'),
        (v_team_id, 'Club Bank Account', 'BANK'),
        (v_team_id, 'bKash / Nagad MFS', 'MFS');

    INSERT INTO public.categories (team_id, name, name_bn, type, is_system) VALUES
        (v_team_id, 'Member Contributions', 'সদস্য চাঁদা', 'INCOME', TRUE),
        (v_team_id, 'Donations', 'অনুদান', 'INCOME', TRUE),
        (v_team_id, 'Sponsorship', 'স্পন্সরশিপ', 'INCOME', TRUE),
        (v_team_id, 'Other Income', 'অন্যান্য আয়', 'INCOME', TRUE),
        (v_team_id, 'Ground Rental', 'মাঠ ভাড়া', 'EXPENSE', TRUE),
        (v_team_id, 'Referee Fees', 'রেফারি ফি', 'EXPENSE', TRUE),
        (v_team_id, 'Transportation', 'যাতায়াত', 'EXPENSE', TRUE),
        (v_team_id, 'Food & Refreshments', 'খাবার ও আপ্যায়ন', 'EXPENSE', TRUE),
        (v_team_id, 'Jerseys', 'জার্সি', 'EXPENSE', TRUE),
        (v_team_id, 'Sports Equipment', 'ক্রীড়া সরঞ্জাম', 'EXPENSE', TRUE),
        (v_team_id, 'Other Expenses', 'অন্যান্য ব্যয়', 'EXPENSE', TRUE);

    INSERT INTO public.audit_logs (team_id, actor_user_id, action_type, entity_type, entity_id, new_values, remarks)
    VALUES (v_team_id, v_user_id, 'TEAM_CREATED', 'teams', v_team_id::TEXT, jsonb_build_object('name', p_team_name), 'Initial Super Admin bootstrap');

    RETURN jsonb_build_object('team_id', v_team_id, 'member_id', v_member_id);
END;
$$;

-- 2. Join Team by Invite Code (Creates PENDING registration for Super Admin approval)
CREATE OR REPLACE FUNCTION public.join_team_by_invite(
    p_invite_code TEXT,
    p_full_name TEXT,
    p_email TEXT,
    p_mobile TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id UUID := auth.uid();
    v_team_id UUID;
    v_count INTEGER;
    v_member_id UUID;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    SELECT id INTO v_team_id FROM public.teams WHERE upper(invite_code) = upper(trim(p_invite_code));
    IF v_team_id IS NULL THEN
        RAISE EXCEPTION 'Invalid invitation code';
    END IF;

    IF EXISTS (SELECT 1 FROM public.team_members WHERE team_id = v_team_id AND lower(email) = lower(p_email)) THEN
        RAISE EXCEPTION 'Duplicate member account for this team';
    END IF;

    INSERT INTO public.users (id, email, full_name, phone, whatsapp_number)
    VALUES (v_user_id, p_email, p_full_name, p_mobile, p_mobile)
    ON CONFLICT (id) DO NOTHING;

    SELECT COUNT(*) + 1 INTO v_count FROM public.team_members WHERE team_id = v_team_id;

    INSERT INTO public.team_members (
        team_id, user_id, member_code, full_name, email, mobile_number, whatsapp_number, role, status, registration_status
    )
    VALUES (
        v_team_id, v_user_id, 'JSC-' || lpad(v_count::TEXT, 3, '0'), p_full_name, p_email, p_mobile, p_mobile, 'MEMBER', 'ACTIVE', 'PENDING'
    )
    RETURNING id INTO v_member_id;

    INSERT INTO public.member_dues (team_id, member_id) VALUES (v_team_id, v_member_id);

    RETURN jsonb_build_object('team_id', v_team_id, 'member_id', v_member_id, 'registration_status', 'PENDING');
END;
$$;

-- 3. Recalculate Member Dues (Attendance-based uses CONFIRMED attendance, preserves historical payments)
CREATE OR REPLACE FUNCTION public.recalculate_member_dues(
    p_team_id UUID,
    p_member_id UUID
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_confirmed_matches INTEGER := 0;
    v_total_matches INTEGER := 0;
    v_attendance_rate NUMERIC(14, 2) := 0;
    v_fixed_per_match NUMERIC(14, 2) := 0;
    v_even_when_absent NUMERIC(14, 2) := 0;
    v_monthly_fixed NUMERIC(14, 2) := 0;
    v_special NUMERIC(14, 2) := 0;
    v_discount NUMERIC(14, 2) := 0;
    v_total_payable NUMERIC(14, 2) := 0;
    v_total_paid NUMERIC(14, 2) := 0;
    v_advance_paid NUMERIC(14, 2) := 0;
    v_applied_advance NUMERIC(14, 2) := 0;
    v_remaining_advance NUMERIC(14, 2) := 0;
    v_additional_charges NUMERIC(14, 2) := 0;
    v_outstanding NUMERIC(14, 2) := 0;
BEGIN
    PERFORM 1 FROM public.member_dues WHERE team_id = p_team_id AND member_id = p_member_id FOR UPDATE;

    SELECT COUNT(*) INTO v_confirmed_matches
    FROM public.attendance
    WHERE team_id = p_team_id AND member_id = p_member_id AND status IN ('PRESENT', 'LATE');

    SELECT COUNT(*) INTO v_total_matches
    FROM public.games
    WHERE team_id = p_team_id AND status IN ('UPCOMING', 'COMPLETED');

    SELECT COALESCE(SUM(amount), 0) INTO v_attendance_rate
    FROM public.contribution_rules WHERE team_id = p_team_id AND is_active = TRUE AND rule_type = 'ATTENDANCE_BASED';

    SELECT COALESCE(SUM(amount), 0) INTO v_fixed_per_match
    FROM public.contribution_rules WHERE team_id = p_team_id AND is_active = TRUE AND rule_type IN ('FIXED_PER_MATCH', 'CUSTOM_PER_MATCH');

    SELECT COALESCE(SUM(amount), 0) INTO v_even_when_absent
    FROM public.contribution_rules WHERE team_id = p_team_id AND is_active = TRUE AND rule_type = 'EVEN_WHEN_ABSENT';

    SELECT COALESCE(SUM(amount), 0) INTO v_monthly_fixed
    FROM public.contribution_rules WHERE team_id = p_team_id AND is_active = TRUE AND rule_type = 'MONTHLY_FIXED';

    SELECT COALESCE(SUM(amount), 0) INTO v_special
    FROM public.contribution_rules WHERE team_id = p_team_id AND is_active = TRUE AND rule_type = 'SPECIAL'
      AND (target_member_id IS NULL OR target_member_id = p_member_id);

    SELECT COALESCE(SUM(amount), 0) INTO v_discount
    FROM public.contribution_rules WHERE team_id = p_team_id AND is_active = TRUE AND rule_type = 'MEMBER_DISCOUNT'
      AND (target_member_id IS NULL OR target_member_id = p_member_id);

    v_total_payable := (v_confirmed_matches * v_attendance_rate)
                     + (v_total_matches * (v_fixed_per_match + v_even_when_absent))
                     + v_monthly_fixed
                     + v_special;

    SELECT COALESCE(SUM(CASE WHEN is_advance = FALSE THEN amount ELSE 0 END), 0),
           COALESCE(SUM(CASE WHEN is_advance = TRUE THEN amount ELSE 0 END), 0)
    INTO v_total_paid, v_advance_paid
    FROM public.transactions
    WHERE team_id = p_team_id
      AND related_member_id = p_member_id
      AND type = 'INCOME'
      AND approval_status = 'APPROVED';

    SELECT COALESCE(additional_charges, 0) INTO v_additional_charges
    FROM public.member_dues WHERE team_id = p_team_id AND member_id = p_member_id;

    -- Apply advance exactly once without double counting
    v_applied_advance := LEAST(GREATEST(0, v_total_payable - v_total_paid - v_discount + v_additional_charges), v_advance_paid);
    v_remaining_advance := GREATEST(0, v_advance_paid - v_applied_advance);
    v_outstanding := GREATEST(0, v_total_payable - v_total_paid - v_applied_advance - v_discount + v_additional_charges);

    INSERT INTO public.member_dues (
        team_id, member_id, total_payable, total_paid, applied_advance, advance_balance, discount_amount, additional_charges, outstanding_balance, last_calculated_at
    )
    VALUES (
        p_team_id, p_member_id, v_total_payable, v_total_paid, v_applied_advance, v_remaining_advance, v_discount, v_additional_charges, v_outstanding, now()
    )
    ON CONFLICT (team_id, member_id) DO UPDATE SET
        total_payable = EXCLUDED.total_payable,
        total_paid = EXCLUDED.total_paid,
        applied_advance = EXCLUDED.applied_advance,
        advance_balance = EXCLUDED.advance_balance,
        discount_amount = EXCLUDED.discount_amount,
        outstanding_balance = EXCLUDED.outstanding_balance,
        last_calculated_at = now();
END;
$$;

-- 4. Record Member Payment (Atomic, prevents duplicate transaction_ref)
CREATE OR REPLACE FUNCTION public.record_member_payment(
    p_team_id UUID,
    p_member_id UUID,
    p_amount NUMERIC,
    p_fund_account_id UUID,
    p_payment_method TEXT,
    p_is_advance BOOLEAN,
    p_description TEXT,
    p_transaction_ref TEXT
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_txn_id UUID;
BEGIN
    IF NOT public.is_treasurer_or_admin(p_team_id) THEN
        RAISE EXCEPTION 'Unauthorized: Treasurer or Super Admin role required';
    END IF;
    IF p_amount <= 0 THEN
        RAISE EXCEPTION 'Payment amount must be greater than zero';
    END IF;

    PERFORM 1 FROM public.fund_accounts WHERE id = p_fund_account_id AND team_id = p_team_id FOR UPDATE;

    INSERT INTO public.transactions (
        team_id, transaction_ref, type, category, amount, description,
        related_member_id, fund_account_id, payment_method, is_advance,
        approval_status, created_by, approved_by
    )
    VALUES (
        p_team_id, p_transaction_ref, 'INCOME', 'Member Contributions', p_amount, p_description,
        p_member_id, p_fund_account_id, p_payment_method, p_is_advance,
        'APPROVED', auth.uid(), auth.uid()
    )
    RETURNING id INTO v_txn_id;

    UPDATE public.fund_accounts
    SET current_balance = current_balance + p_amount
    WHERE id = p_fund_account_id;

    PERFORM public.recalculate_member_dues(p_team_id, p_member_id);

    INSERT INTO public.audit_logs (team_id, actor_user_id, action_type, entity_type, entity_id, new_values, remarks)
    VALUES (p_team_id, auth.uid(), 'PAYMENT_RECORDED', 'transactions', v_txn_id::TEXT,
            jsonb_build_object('member_id', p_member_id, 'amount', p_amount, 'is_advance', p_is_advance), p_description);

    RETURN v_txn_id;
END;
$$;

-- 5. Approve / Reject Expense Request (Super Admin ONLY)
CREATE OR REPLACE FUNCTION public.review_expense_request(
    p_request_id UUID,
    p_decision TEXT,
    p_remarks TEXT DEFAULT ''
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_req public.expense_requests%ROWTYPE;
    v_txn_ref TEXT;
BEGIN
    SELECT * INTO v_req FROM public.expense_requests WHERE id = p_request_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Expense request not found';
    END IF;

    IF NOT public.is_super_admin(v_req.team_id) THEN
        RAISE EXCEPTION 'Only Super Admin can approve or reject expense requests';
    END IF;

    IF v_req.status <> 'PENDING' AND v_req.status <> 'MODIFICATION_REQUESTED' THEN
        RAISE EXCEPTION 'Expense request already finalized';
    END IF;

    UPDATE public.expense_requests
    SET status = p_decision,
        approval_remarks = p_remarks,
        reviewed_by = auth.uid(),
        reviewed_at = now(),
        updated_at = now()
    WHERE id = p_request_id;

    IF p_decision = 'APPROVED' THEN
        v_txn_ref := 'EXP-' || upper(substr(replace(p_request_id::TEXT, '-', ''), 1, 10));

        IF v_req.fund_account_id IS NOT NULL THEN
            PERFORM 1 FROM public.fund_accounts WHERE id = v_req.fund_account_id FOR UPDATE;
            UPDATE public.fund_accounts
            SET current_balance = current_balance - v_req.requested_amount
            WHERE id = v_req.fund_account_id;
        END IF;

        INSERT INTO public.transactions (
            team_id, transaction_ref, type, category, amount, txn_date, description,
            related_game_id, fund_account_id, payment_method, receipt_url,
            approval_status, created_by, approved_by
        )
        VALUES (
            v_req.team_id, v_txn_ref, 'EXPENSE', v_req.category, v_req.requested_amount, v_req.submission_date, v_req.description,
            v_req.related_game_id, v_req.fund_account_id, 'CASH', v_req.receipt_url,
            'APPROVED', v_req.requested_by, auth.uid()
        );
    END IF;

    INSERT INTO public.audit_logs (team_id, actor_user_id, action_type, entity_type, entity_id, previous_values, new_values, remarks)
    VALUES (v_req.team_id, auth.uid(), 'EXPENSE_' || p_decision, 'expense_requests', p_request_id::TEXT,
            jsonb_build_object('status', v_req.status), jsonb_build_object('status', p_decision, 'amount', v_req.requested_amount), p_remarks);
END;
$$;

-- 6. Execute Balanced Fund Transfer
CREATE OR REPLACE FUNCTION public.execute_fund_transfer(
    p_team_id UUID,
    p_from_account_id UUID,
    p_to_account_id UUID,
    p_amount NUMERIC,
    p_notes TEXT DEFAULT ''
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_transfer_id UUID;
    v_from_bal NUMERIC;
    v_ref TEXT;
BEGIN
    IF NOT public.is_treasurer_or_admin(p_team_id) THEN
        RAISE EXCEPTION 'Unauthorized';
    END IF;
    IF p_amount <= 0 THEN
        RAISE EXCEPTION 'Amount must be positive';
    END IF;

    SELECT current_balance INTO v_from_bal
    FROM public.fund_accounts WHERE id = p_from_account_id AND team_id = p_team_id FOR UPDATE;
    IF v_from_bal < p_amount THEN
        RAISE EXCEPTION 'Insufficient fund balance in source account';
    END IF;

    PERFORM 1 FROM public.fund_accounts WHERE id = p_to_account_id AND team_id = p_team_id FOR UPDATE;

    UPDATE public.fund_accounts SET current_balance = current_balance - p_amount WHERE id = p_from_account_id;
    UPDATE public.fund_accounts SET current_balance = current_balance + p_amount WHERE id = p_to_account_id;

    v_ref := 'TRF-' || upper(substr(encode(gen_random_bytes(5), 'hex'), 1, 10));

    INSERT INTO public.fund_transfers (team_id, from_account_id, to_account_id, amount, reference_code, notes, created_by)
    VALUES (p_team_id, p_from_account_id, p_to_account_id, p_amount, v_ref, p_notes, auth.uid())
    RETURNING id INTO v_transfer_id;

    INSERT INTO public.audit_logs (team_id, actor_user_id, action_type, entity_type, entity_id, new_values, remarks)
    VALUES (p_team_id, auth.uid(), 'FUND_TRANSFER', 'fund_transfers', v_transfer_id::TEXT,
            jsonb_build_object('from', p_from_account_id, 'to', p_to_account_id, 'amount', p_amount), p_notes);

    RETURN v_transfer_id;
END;
$$;

-- 7. Automatic Audit Trigger on Critical Tables
CREATE OR REPLACE FUNCTION public.audit_critical_changes()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    INSERT INTO public.audit_logs (team_id, actor_user_id, action_type, entity_type, entity_id, previous_values, new_values)
    VALUES (
        COALESCE(NEW.team_id, OLD.team_id),
        auth.uid(),
        TG_OP,
        TG_TABLE_NAME,
        COALESCE(NEW.id::TEXT, OLD.id::TEXT),
        CASE WHEN TG_OP IN ('UPDATE', 'DELETE') THEN to_jsonb(OLD) ELSE NULL END,
        CASE WHEN TG_OP IN ('INSERT', 'UPDATE') THEN to_jsonb(NEW) ELSE NULL END
    );
    RETURN COALESCE(NEW, OLD);
END;
$$;

CREATE TRIGGER trg_audit_attendance AFTER INSERT OR UPDATE ON public.attendance
    FOR EACH ROW EXECUTE FUNCTION public.audit_critical_changes();
CREATE TRIGGER trg_audit_availability AFTER UPDATE ON public.availability
    FOR EACH ROW EXECUTE FUNCTION public.audit_critical_changes();
CREATE TRIGGER trg_audit_team_members AFTER UPDATE ON public.team_members
    FOR EACH ROW EXECUTE FUNCTION public.audit_critical_changes();
