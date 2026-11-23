package com.example.data.repository

import com.example.core.i18n.Formatters
import com.example.core.preferences.SessionPreferences
import com.example.data.remote.SupabaseClient
import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID

data class TeamWorkspaceSnapshot(
    val team: Team,
    val settings: TeamSettings,
    val currentMember: TeamMember,
    val members: List<TeamMember>,
    val matches: List<MatchGame>,
    val availability: List<AvailabilityRecord>,
    val attendance: List<AttendanceRecord>,
    val leaveRequests: List<LeaveRequest>,
    val contributionRules: List<ContributionRule>,
    val memberDues: List<MemberDue>,
    val fundAccounts: List<FundAccount>,
    val transactions: List<FinancialTransaction>,
    val fundTransfers: List<FundTransfer>,
    val expenseRequests: List<ExpenseRequest>,
    val budgets: List<BudgetItem>,
    val fundGoals: List<FundGoal>,
    val notifications: List<NotificationItem>,
    val announcements: List<Announcement>,
    val polls: List<TeamPoll>,
    val pollOptions: List<PollOption>,
    val pollVotes: List<PollVote>,
    val galleryAlbums: List<GalleryAlbum>,
    val galleryItems: List<GalleryItem>,
    val matchResults: List<MatchResult>,
    val playerStats: List<PlayerMatchStat>,
    val equipment: List<EquipmentItem>,
    val equipmentAssignments: List<EquipmentAssignment>,
    val jerseys: List<JerseyItem>,
    val whatsappLogs: List<WhatsAppLog>,
    val monthlyReports: List<MonthlyReportRecord>,
    val auditLogs: List<AuditLogEntry>
)

class ClubRepository(
    val api: SupabaseClient,
    val prefs: SessionPreferences
) {
    private suspend fun token(): String {
        val session = prefs.sessionFlow.first()
        if (session.accessToken.isBlank()) throw IllegalStateException("Session expired. Please sign in.")
        return session.accessToken
    }

    fun observeRealtime(teamId: String, accessToken: String): Flow<String> =
        api.observeTeamRealtime(teamId, accessToken)

    // ---- Auth & Onboarding ----

    suspend fun signIn(email: String, password: String): AuthResponseDto {
        val auth = api.signInWithPassword(email, password)
        val uid = auth.user?.id ?: throw IllegalStateException("Auth user missing")
        prefs.saveAuthSession(auth.accessToken, auth.refreshToken, uid, auth.user.email ?: email)
        val memberships = api.selectList<TeamMember>(
            "team_members",
            "user_id=eq.$uid&registration_status=eq.APPROVED&select=*",
            auth.accessToken
        )
        memberships.firstOrNull()?.let { prefs.saveActiveTeamId(it.teamId) }
        return auth
    }

    suspend fun signUpAndCreateTeam(
        email: String,
        password: String,
        fullName: String,
        mobile: String,
        teamName: String
    ): String {
        val auth = api.signUp(email, password, fullName)
        if (auth.accessToken.isBlank()) {
            throw IllegalStateException("Check your email to verify your account before signing in.")
        }
        val uid = auth.user?.id ?: ""
        prefs.saveAuthSession(auth.accessToken, auth.refreshToken, uid, email)
        val res = api.rpcRaw(
            "bootstrap_team",
            buildJsonObject {
                put("p_team_name", teamName.trim())
                put("p_short_name", teamName.trim().take(4).uppercase())
                put("p_full_name", fullName.trim())
                put("p_email", email.trim())
                put("p_mobile", mobile.trim())
            },
            auth.accessToken
        )
        val obj = api.json.parseToJsonElement(res).jsonObject
        val teamId = obj["team_id"]?.jsonPrimitive?.content.orEmpty()
        if (teamId.isNotBlank()) prefs.saveActiveTeamId(teamId)
        return teamId
    }

    suspend fun signUpAndJoinWithInvite(
        email: String,
        password: String,
        fullName: String,
        mobile: String,
        inviteCode: String
    ) {
        val auth = api.signUp(email, password, fullName)
        if (auth.accessToken.isBlank()) {
            throw IllegalStateException("Verification email sent. Please confirm email and sign in.")
        }
        val uid = auth.user?.id ?: ""
        prefs.saveAuthSession(auth.accessToken, auth.refreshToken, uid, email)
        val res = api.rpcRaw(
            "join_team_by_invite",
            buildJsonObject {
                put("p_invite_code", inviteCode.trim())
                put("p_full_name", fullName.trim())
                put("p_email", email.trim())
                put("p_mobile", mobile.trim())
            },
            auth.accessToken
        )
        val obj = api.json.parseToJsonElement(res).jsonObject
        val teamId = obj["team_id"]?.jsonPrimitive?.content.orEmpty()
        if (teamId.isNotBlank()) prefs.saveActiveTeamId(teamId)
    }

    suspend fun forgotPassword(email: String) = api.recoverPassword(email)

    suspend fun signOut() {
        val s = prefs.sessionFlow.first()
        api.signOut(s.accessToken)
        prefs.clearAuthSession()
    }

    suspend fun syncFcmToken(fcmToken: String) {
        val s = prefs.sessionFlow.first()
        if (s.accessToken.isBlank() || s.userId.isBlank()) return
        runCatching {
            api.patchRaw(
                "users",
                "id=eq.${s.userId}",
                buildJsonObject { put("fcm_token", fcmToken) }.toString(),
                s.accessToken
            )
        }
    }

    // ---- Workspace Loader ----

    suspend fun loadWorkspace(): TeamWorkspaceSnapshot {
        val s = prefs.sessionFlow.first()
        var tk = s.accessToken
        if (tk.isBlank()) throw IllegalStateException("Not authenticated")

        val myMemberships = try {
            api.selectList<TeamMember>("team_members", "user_id=eq.${s.userId}&select=*", tk)
        } catch (e: Exception) {
            if (s.refreshToken.isNotBlank()) {
                val refreshed = api.refreshSession(s.refreshToken)
                tk = refreshed.accessToken
                prefs.saveAuthSession(tk, refreshed.refreshToken, s.userId, s.userEmail, s.activeTeamId)
                api.selectList("team_members", "user_id=eq.${s.userId}&select=*", tk)
            } else {
                throw e
            }
        }

        val currentMember = myMemberships.firstOrNull { it.teamId == s.activeTeamId }
            ?: myMemberships.firstOrNull()
            ?: throw IllegalStateException("No team membership found for this account.")

        if (currentMember.regStatus == RegistrationStatus.PENDING) {
            throw IllegalStateException("Your registration is awaiting Super Admin approval.")
        }
        if (currentMember.regStatus == RegistrationStatus.REJECTED || currentMember.memberStatus == MemberStatus.SUSPENDED) {
            throw IllegalStateException("Your account is currently suspended or rejected.")
        }

        val tid = currentMember.teamId
        if (tid != s.activeTeamId) prefs.saveActiveTeamId(tid)

        val teams = api.selectList<Team>("teams", "id=eq.$tid&select=*", tk)
        val team = teams.firstOrNull() ?: throw IllegalStateException("Team not found")
        val settings = api.selectList<TeamSettings>("team_settings", "team_id=eq.$tid&select=*", tk).firstOrNull()
            ?: TeamSettings(teamId = tid)

        val members = api.selectList<TeamMember>("team_members", "team_id=eq.$tid&order=member_code.asc&select=*", tk)
        val matches = api.selectList<MatchGame>("games", "team_id=eq.$tid&order=match_date.asc&select=*", tk)
        val availability = api.selectList<AvailabilityRecord>("availability", "team_id=eq.$tid&select=*", tk)
        val attendance = api.selectList<AttendanceRecord>("attendance", "team_id=eq.$tid&select=*", tk)
        val leaveRequests = api.selectList<LeaveRequest>("leave_requests", "team_id=eq.$tid&order=created_at.desc&select=*", tk)
        val rules = api.selectList<ContributionRule>("contribution_rules", "team_id=eq.$tid&order=created_at.desc&select=*", tk)
        val dues = api.selectList<MemberDue>("member_dues", "team_id=eq.$tid&select=*", tk)
        val accounts = api.selectList<FundAccount>("fund_accounts", "team_id=eq.$tid&order=name.asc&select=*", tk)
        val txns = api.selectList<FinancialTransaction>("transactions", "team_id=eq.$tid&order=txn_date.desc,created_at.desc&select=*", tk)
        val transfers = runCatching { api.selectList<FundTransfer>("fund_transfers", "team_id=eq.$tid&order=created_at.desc&select=*", tk) }.getOrDefault(emptyList())
        val expenseReqs = runCatching { api.selectList<ExpenseRequest>("expense_requests", "team_id=eq.$tid&order=created_at.desc&select=*", tk) }.getOrDefault(emptyList())
        val budgets = api.selectList<BudgetItem>("budgets", "team_id=eq.$tid&select=*", tk)
        val goals = api.selectList<FundGoal>("fund_goals", "team_id=eq.$tid&select=*", tk)
        val notifs = api.selectList<NotificationItem>("notifications", "team_id=eq.$tid&order=created_at.desc&limit=50&select=*", tk)
        val announcements = api.selectList<Announcement>("announcements", "team_id=eq.$tid&order=is_pinned.desc,created_at.desc&select=*", tk)
        val polls = api.selectList<TeamPoll>("team_polls", "team_id=eq.$tid&order=created_at.desc&select=*", tk)
        val pollOpts = api.selectList<PollOption>("poll_options", "team_id=eq.$tid&select=*", tk)
        val pollVotes = api.selectList<PollVote>("poll_votes", "team_id=eq.$tid&select=*", tk)
        val albums = api.selectList<GalleryAlbum>("gallery_albums", "team_id=eq.$tid&order=album_date.desc&select=*", tk)
        val gallery = api.selectList<GalleryItem>("team_gallery", "team_id=eq.$tid&is_archived=eq.false&order=created_at.desc&select=*", tk)
        val results = api.selectList<MatchResult>("match_results", "team_id=eq.$tid&order=match_date.desc&select=*", tk)
        val stats = api.selectList<PlayerMatchStat>("player_match_statistics", "team_id=eq.$tid&select=*", tk)
        val equipment = api.selectList<EquipmentItem>("equipment", "team_id=eq.$tid&order=name.asc&select=*", tk)
        val assignments = api.selectList<EquipmentAssignment>("equipment_assignments", "team_id=eq.$tid&order=created_at.desc&select=*", tk)
        val jerseys = api.selectList<JerseyItem>("jerseys", "team_id=eq.$tid&order=jersey_number.asc&select=*", tk)
        val waLogs = runCatching { api.selectList<WhatsAppLog>("whatsapp_logs", "team_id=eq.$tid&order=created_at.desc&limit=40&select=*", tk) }.getOrDefault(emptyList())
        val reports = api.selectList<MonthlyReportRecord>("monthly_reports", "team_id=eq.$tid&order=report_period.desc&select=*", tk)
        val audits = runCatching { api.selectList<AuditLogEntry>("audit_logs", "team_id=eq.$tid&order=created_at.desc&limit=80&select=*", tk) }.getOrDefault(emptyList())

        return TeamWorkspaceSnapshot(
            team = team,
            settings = settings,
            currentMember = currentMember,
            members = members,
            matches = matches,
            availability = availability,
            attendance = attendance,
            leaveRequests = leaveRequests,
            contributionRules = rules,
            memberDues = dues,
            fundAccounts = accounts,
            transactions = txns,
            fundTransfers = transfers,
            expenseRequests = expenseReqs,
            budgets = budgets,
            fundGoals = goals,
            notifications = notifs,
            announcements = announcements,
            polls = polls,
            pollOptions = pollOpts,
            pollVotes = pollVotes,
            galleryAlbums = albums,
            galleryItems = gallery,
            matchResults = results,
            playerStats = stats,
            equipment = equipment,
            equipmentAssignments = assignments,
            jerseys = jerseys,
            whatsappLogs = waLogs,
            monthlyReports = reports,
            auditLogs = audits
        )
    }

    // ---- Member Management ----

    suspend fun addMember(
        teamId: String,
        fullName: String,
        email: String,
        mobile: String,
        whatsapp: String,
        dob: String?,
        jerseySize: String,
        position: String,
        role: UserRole,
        nextCode: String
    ) {
        val tk = token()
        val body = buildJsonObject {
            put("team_id", teamId)
            put("member_code", nextCode)
            put("full_name", fullName.trim())
            put("email", email.trim())
            put("mobile_number", mobile.trim())
            put("whatsapp_number", whatsapp.trim())
            if (!dob.isNullOrBlank()) put("date_of_birth", dob)
            put("jersey_size", jerseySize)
            put("preferred_position", position)
            put("role", role.name)
            put("status", "ACTIVE")
            put("registration_status", "APPROVED")
        }
        val inserted = api.insertRaw("team_members", body.toString(), tk)
        val created = api.json.decodeFromString<List<TeamMember>>(inserted).firstOrNull()
        created?.let {
            api.insertRaw(
                "member_dues",
                buildJsonObject { put("team_id", teamId); put("member_id", it.id) }.toString(),
                tk
            )
        }
    }

    suspend fun updateMemberStatusAndRole(memberId: String, role: UserRole, status: MemberStatus, regStatus: RegistrationStatus) {
        val tk = token()
        api.patchRaw(
            "team_members",
            "id=eq.$memberId",
            buildJsonObject {
                put("role", role.name)
                put("status", status.name)
                put("registration_status", regStatus.name)
            }.toString(),
            tk
        )
    }

    suspend fun updateOwnProfile(memberId: String, fullName: String, mobile: String, whatsapp: String, dob: String?, jerseySize: String, position: String, emergency: String?) {
        val tk = token()
        val body = buildJsonObject {
            put("full_name", fullName.trim())
            put("mobile_number", mobile.trim())
            put("whatsapp_number", whatsapp.trim())
            if (!dob.isNullOrBlank()) put("date_of_birth", dob)
            put("jersey_size", jerseySize)
            put("preferred_position", position)
            if (!emergency.isNullOrBlank()) put("emergency_contact", emergency)
        }
        api.patchRaw("team_members", "id=eq.$memberId", body.toString(), tk)
    }

    // ---- Availability & Matches (NO match-date polling!) ----

    suspend fun setAvailabilityDates(
        teamId: String,
        memberId: String,
        dates: List<String>,
        status: AvailabilityStatus,
        remarks: String?,
        overriddenByUserId: String? = null
    ) {
        val tk = token()
        val array = buildJsonArray {
            dates.forEach { d ->
                add(buildJsonObject {
                    put("team_id", teamId)
                    put("member_id", memberId)
                    put("avail_date", d)
                    put("status", status.name)
                    if (!remarks.isNullOrBlank()) put("remarks", remarks)
                    if (overriddenByUserId != null) put("overridden_by", overriddenByUserId)
                })
            }
        }
        api.insertRaw("availability", array.toString(), tk, upsertConflict = "team_id,member_id,avail_date")
    }

    suspend fun createMatch(
        teamId: String,
        userId: String,
        title: String,
        opponent: String,
        date: String,
        time: String,
        venue: String,
        mapsUrl: String?,
        description: String?,
        expectedBudget: Double
    ) {
        val tk = token()
        val body = buildJsonObject {
            put("team_id", teamId)
            put("title", title.trim())
            put("opponent_name", opponent.trim())
            put("match_date", date)
            put("match_time", time)
            put("venue_name", venue.trim())
            if (!mapsUrl.isNullOrBlank()) put("maps_url", mapsUrl.trim())
            if (!description.isNullOrBlank()) put("description", description.trim())
            put("expected_budget", expectedBudget)
            put("status", "UPCOMING")
            put("created_by", userId)
        }
        api.insertRaw("games", body.toString(), tk)
        broadcastTeamNotification(teamId, "NEW_MATCH", "নতুন ম্যাচ: $title", "$date — $venue ($opponent)")
    }

    suspend fun updateMatchStatus(matchId: String, status: MatchStatus, newDate: String? = null, newTime: String? = null) {
        val tk = token()
        val body = buildJsonObject {
            put("status", status.name)
            if (!newDate.isNullOrBlank()) put("match_date", newDate)
            if (!newTime.isNullOrBlank()) put("match_time", newTime)
        }
        api.patchRaw("games", "id=eq.$matchId", body.toString(), tk)
    }

    // ---- Attendance & Leave Requests ----

    suspend fun recordAttendance(teamId: String, gameId: String, memberId: String, status: AttendanceStatus, reason: String?, recordedBy: String) {
        val tk = token()
        val body = buildJsonObject {
            put("team_id", teamId)
            put("game_id", gameId)
            put("member_id", memberId)
            put("status", status.name)
            if (!reason.isNullOrBlank()) put("absence_reason", reason)
            put("recorded_by", recordedBy)
        }
        api.insertRaw("attendance", body.toString(), tk, upsertConflict = "game_id,member_id")
        api.rpcRaw("recalculate_member_dues", buildJsonObject { put("p_team_id", teamId); put("p_member_id", memberId) }, tk)
    }

    suspend fun submitLeaveRequest(teamId: String, memberId: String, date: String, reason: String, note: String?) {
        val tk = token()
        val body = buildJsonObject {
            put("team_id", teamId)
            put("member_id", memberId)
            put("leave_date", date)
            put("reason", reason.trim())
            if (!note.isNullOrBlank()) put("additional_note", note.trim())
        }
        api.insertRaw("leave_requests", body.toString(), tk)
    }

    suspend fun reviewLeaveRequest(requestId: String, decision: String, remarks: String, reviewerId: String) {
        val tk = token()
        api.patchRaw(
            "leave_requests",
            "id=eq.$requestId",
            buildJsonObject {
                put("status", decision)
                put("admin_remarks", remarks)
                put("reviewed_by", reviewerId)
                put("reviewed_at", Formatters.nowDhakaIso())
            }.toString(),
            tk
        )
    }

    // ---- Contributions, Payments, Expenses, Transfers, Budgets ----

    suspend fun createContributionRule(teamId: String, name: String, ruleType: String, amount: Double, targetMemberId: String?, userId: String) {
        val tk = token()
        api.insertRaw(
            "contribution_rules",
            buildJsonObject {
                put("team_id", teamId)
                put("name", name.trim())
                put("rule_type", ruleType)
                put("amount", amount)
                if (!targetMemberId.isNullOrBlank()) put("target_member_id", targetMemberId)
                put("created_by", userId)
            }.toString(),
            tk
        )
    }

    suspend fun recalculateAllMemberDues(teamId: String, members: List<TeamMember>) {
        val tk = token()
        members.forEach { m ->
            api.rpcRaw("recalculate_member_dues", buildJsonObject { put("p_team_id", teamId); put("p_member_id", m.id) }, tk)
        }
    }

    suspend fun recordMemberPayment(
        teamId: String,
        memberId: String,
        amount: Double,
        fundAccountId: String,
        paymentMethod: String,
        isAdvance: Boolean,
        description: String
    ) {
        val tk = token()
        val ref = "PAY-${UUID.randomUUID().toString().take(8).uppercase()}"
        api.rpcRaw(
            "record_member_payment",
            buildJsonObject {
                put("p_team_id", teamId)
                put("p_member_id", memberId)
                put("p_amount", amount)
                put("p_fund_account_id", fundAccountId)
                put("p_payment_method", paymentMethod)
                put("p_is_advance", isAdvance)
                put("p_description", description.ifBlank { "Member contribution payment" })
                put("p_transaction_ref", ref)
            },
            tk
        )
    }

    suspend fun recordDirectIncome(teamId: String, category: String, amount: Double, accountId: String, method: String, desc: String, userId: String) {
        val tk = token()
        val ref = "INC-${UUID.randomUUID().toString().take(8).uppercase()}"
        api.insertRaw(
            "transactions",
            buildJsonObject {
                put("team_id", teamId)
                put("transaction_ref", ref)
                put("type", "INCOME")
                put("category", category)
                put("amount", amount)
                put("txn_date", Formatters.todayDhaka().toString())
                put("description", desc.trim())
                put("fund_account_id", accountId)
                put("payment_method", method)
                put("approval_status", "APPROVED")
                put("created_by", userId)
                put("approved_by", userId)
            }.toString(),
            tk
        )
        val acc = api.selectList<FundAccount>("fund_accounts", "id=eq.$accountId&select=*", tk).firstOrNull()
        acc?.let {
            api.patchRaw("fund_accounts", "id=eq.$accountId", buildJsonObject { put("current_balance", it.currentBalance + amount) }.toString(), tk)
        }
    }

    suspend fun submitExpenseRequest(teamId: String, amount: Double, category: String, description: String, gameId: String?, accountId: String?, userId: String) {
        val tk = token()
        api.insertRaw(
            "expense_requests",
            buildJsonObject {
                put("team_id", teamId)
                put("requested_amount", amount)
                put("category", category)
                put("description", description.trim())
                if (!gameId.isNullOrBlank()) put("related_game_id", gameId)
                if (!accountId.isNullOrBlank()) put("fund_account_id", accountId)
                put("submission_date", Formatters.todayDhaka().toString())
                put("requested_by", userId)
            }.toString(),
            tk
        )
    }

    suspend fun reviewExpenseRequest(requestId: String, decision: String, remarks: String) {
        val tk = token()
        api.rpcRaw(
            "review_expense_request",
            buildJsonObject {
                put("p_request_id", requestId)
                put("p_decision", decision)
                put("p_remarks", remarks)
            },
            tk
        )
    }

    suspend fun executeFundTransfer(teamId: String, fromId: String, toId: String, amount: Double, notes: String) {
        val tk = token()
        api.rpcRaw(
            "execute_fund_transfer",
            buildJsonObject {
                put("p_team_id", teamId)
                put("p_from_account_id", fromId)
                put("p_to_account_id", toId)
                put("p_amount", amount)
                put("p_notes", notes)
            },
            tk
        )
    }

    suspend fun createBudgetOrGoal(teamId: String, isBudget: Boolean, title: String, amount: Double, periodOrDeadline: String, userId: String) {
        val tk = token()
        if (isBudget) {
            api.insertRaw(
                "budgets",
                buildJsonObject {
                    put("team_id", teamId)
                    put("title", title.trim())
                    put("budget_type", "MONTHLY")
                    put("period_key", periodOrDeadline)
                    put("budget_amount", amount)
                    put("created_by", userId)
                }.toString(),
                tk
            )
        } else {
            api.insertRaw(
                "fund_goals",
                buildJsonObject {
                    put("team_id", teamId)
                    put("title", title.trim())
                    put("goal_type", "SPECIAL_FUNDRAISING")
                    put("target_amount", amount)
                    if (periodOrDeadline.isNotBlank()) put("deadline", periodOrDeadline)
                }.toString(),
                tk
            )
        }
    }

    suspend fun saveMonthlyReportRecord(
        teamId: String,
        period: String,
        opening: Double,
        contributions: Double,
        otherIncome: Double,
        expenses: Double,
        closing: Double,
        outstanding: Double,
        advance: Double,
        userId: String
    ) {
        val tk = token()
        api.insertRaw(
            "monthly_reports",
            buildJsonObject {
                put("team_id", teamId)
                put("report_period", period)
                put("opening_balance", opening)
                put("total_contributions", contributions)
                put("other_income", otherIncome)
                put("total_expenses", expenses)
                put("closing_balance", closing)
                put("outstanding_dues", outstanding)
                put("advance_payments", advance)
                put("generated_by", userId)
            }.toString(),
            tk,
            upsertConflict = "team_id,report_period"
        )
    }

    // ---- Announcements, Polls, Results, Gallery, Equipment, Jerseys, WhatsApp ----

    suspend fun publishAnnouncement(teamId: String, title: String, content: String, isPinned: Boolean, userId: String) {
        val tk = token()
        api.insertRaw(
            "announcements",
            buildJsonObject {
                put("team_id", teamId)
                put("title", title.trim())
                put("content", content.trim())
                put("is_pinned", isPinned)
                put("created_by", userId)
            }.toString(),
            tk
        )
        broadcastTeamNotification(teamId, "NEW_ANNOUNCEMENT", title.trim(), content.trim().take(120))
    }

    suspend fun createGeneralPoll(
        teamId: String,
        title: String,
        category: String,
        options: List<String>,
        isAnonymous: Boolean,
        deadlineIso: String,
        userId: String
    ) {
        val tk = token()
        val inserted = api.insertRaw(
            "team_polls",
            buildJsonObject {
                put("team_id", teamId)
                put("title", title.trim())
                put("poll_category", category)
                put("is_anonymous", isAnonymous)
                put("deadline", deadlineIso)
                put("created_by", userId)
            }.toString(),
            tk
        )
        val poll = api.json.decodeFromString<List<TeamPoll>>(inserted).firstOrNull() ?: return
        val optArr = buildJsonArray {
            options.filter { it.isNotBlank() }.forEach { opt ->
                add(buildJsonObject {
                    put("team_id", teamId)
                    put("poll_id", poll.id)
                    put("option_text", opt.trim())
                })
            }
        }
        api.insertRaw("poll_options", optArr.toString(), tk)
    }

    suspend fun castPollVote(teamId: String, pollId: String, optionId: String, memberId: String) {
        val tk = token()
        api.insertRaw(
            "poll_votes",
            buildJsonObject {
                put("team_id", teamId)
                put("poll_id", pollId)
                put("option_id", optionId)
                put("member_id", memberId)
            }.toString(),
            tk
        )
    }

    suspend fun saveMatchResult(
        teamId: String,
        gameId: String,
        opponent: String,
        matchDate: String,
        teamScore: Int,
        opponentScore: Int,
        motmMemberId: String?,
        notes: String?,
        userId: String
    ) {
        val tk = token()
        val outcome = when {
            teamScore > opponentScore -> "WIN"
            teamScore < opponentScore -> "LOSS"
            else -> "DRAW"
        }
        api.insertRaw(
            "match_results",
            buildJsonObject {
                put("team_id", teamId)
                put("game_id", gameId)
                put("opponent_name", opponent)
                put("match_date", matchDate)
                put("team_score", teamScore)
                put("opponent_score", opponentScore)
                put("outcome", outcome)
                if (!motmMemberId.isNullOrBlank()) put("man_of_the_match_member_id", motmMemberId)
                if (!notes.isNullOrBlank()) put("match_notes", notes)
                put("created_by", userId)
            }.toString(),
            tk,
            upsertConflict = "game_id"
        )
        api.patchRaw("games", "id=eq.$gameId", buildJsonObject { put("status", "COMPLETED") }.toString(), tk)
    }

    suspend fun uploadGalleryMedia(
        teamId: String,
        mediaType: String,
        bytes: ByteArray,
        mimeType: String,
        extension: String,
        caption: String,
        userId: String
    ) {
        val tk = token()
        val path = "$teamId/${System.currentTimeMillis()}.$extension"
        val publicUrl = api.uploadStorageObject("team-gallery", path, bytes, mimeType, tk)
        api.insertRaw(
            "team_gallery",
            buildJsonObject {
                put("team_id", teamId)
                put("media_type", mediaType)
                put("media_url", publicUrl)
                put("caption", caption.trim())
                put("uploaded_by", userId)
            }.toString(),
            tk
        )
    }

    suspend fun addEquipmentOrJersey(
        teamId: String,
        isJersey: Boolean,
        nameOrSize: String,
        categoryOrNumber: String,
        qtyOrMemberId: String,
        cost: Double,
        location: String
    ) {
        val tk = token()
        if (isJersey) {
            api.insertRaw(
                "jerseys",
                buildJsonObject {
                    put("team_id", teamId)
                    put("jersey_number", categoryOrNumber.toIntOrNull() ?: 10)
                    put("size", nameOrSize)
                    if (qtyOrMemberId.isNotBlank()) put("assigned_member_id", qtyOrMemberId)
                    put("issue_date", Formatters.todayDhaka().toString())
                }.toString(),
                tk
            )
        } else {
            val qty = qtyOrMemberId.toIntOrNull() ?: 1
            api.insertRaw(
                "equipment",
                buildJsonObject {
                    put("team_id", teamId)
                    put("name", nameOrSize.trim())
                    put("category", categoryOrNumber.ifBlank { "Football" })
                    put("quantity", qty)
                    put("available_quantity", qty)
                    put("purchase_cost", cost)
                    put("storage_location", location.ifBlank { "Club Room" })
                }.toString(),
                tk
            )
        }
    }

    suspend fun checkoutEquipment(teamId: String, equipment: EquipmentItem, memberId: String, qty: Int, userId: String) {
        val tk = token()
        api.insertRaw(
            "equipment_assignments",
            buildJsonObject {
                put("team_id", teamId)
                put("equipment_id", equipment.id)
                put("member_id", memberId)
                put("quantity", qty)
                put("status", "CHECKED_OUT")
                put("created_by", userId)
            }.toString(),
            tk
        )
        val newAvail = (equipment.availableQuantity - qty).coerceAtLeast(0)
        api.patchRaw("equipment", "id=eq.${equipment.id}", buildJsonObject { put("available_quantity", newAvail) }.toString(), tk)
    }

    suspend fun returnOrReportEquipment(assignment: EquipmentAssignment, equipment: EquipmentItem?, newStatus: String) {
        val tk = token()
        api.patchRaw(
            "equipment_assignments",
            "id=eq.${assignment.id}",
            buildJsonObject {
                put("status", newStatus)
                put("return_date", Formatters.todayDhaka().toString())
            }.toString(),
            tk
        )
        if (newStatus == "RETURNED" && equipment != null) {
            val newAvail = (equipment.availableQuantity + assignment.quantity).coerceAtMost(equipment.quantity)
            api.patchRaw("equipment", "id=eq.${equipment.id}", buildJsonObject { put("available_quantity", newAvail) }.toString(), tk)
        } else if ((newStatus == "DAMAGED" || newStatus == "LOST") && equipment != null) {
            api.patchRaw("equipment", "id=eq.${equipment.id}", buildJsonObject { put("condition", newStatus) }.toString(), tk)
        }
    }

    suspend fun sendWhatsAppViaEdge(
        teamId: String,
        phone: String,
        memberId: String?,
        template: String,
        preview: String
    ): String {
        val tk = token()
        return api.invokeEdgeFunction(
            "send-whatsapp",
            buildJsonObject {
                put("team_id", teamId)
                put("recipient_phone", phone)
                if (!memberId.isNullOrBlank()) put("recipient_member_id", memberId)
                put("template_name", template)
                put("message_preview", preview)
            },
            tk
        )
    }

    suspend fun logManualWhatsAppShare(teamId: String, phone: String, preview: String) {
        val tk = token()
        api.insertRaw(
            "whatsapp_logs",
            buildJsonObject {
                put("team_id", teamId)
                put("recipient_phone", phone)
                put("template_name", "MANUAL_SHARE")
                put("message_preview", preview)
                put("delivery_status", "SHARED_MANUALLY")
            }.toString(),
            tk
        )
    }

    suspend fun updateTeamSettings(teamId: String, whatsappEnabled: Boolean, allowViewOthers: Boolean, deadlineHours: Int, userId: String) {
        val tk = token()
        api.patchRaw(
            "team_settings",
            "team_id=eq.$teamId",
            buildJsonObject {
                put("whatsapp_enabled", whatsappEnabled)
                put("allow_members_view_others_availability", allowViewOthers)
                put("availability_deadline_hours_before", deadlineHours)
                put("updated_by", userId)
            }.toString(),
            tk
        )
    }

    suspend fun markNotificationRead(notificationId: String) {
        val tk = token()
        api.patchRaw("notifications", "id=eq.$notificationId", buildJsonObject { put("is_read", true) }.toString(), tk)
    }

    private suspend fun broadcastTeamNotification(teamId: String, eventType: String, title: String, body: String) {
        val tk = token()
        runCatching {
            api.insertRaw(
                "notifications",
                buildJsonObject {
                    put("team_id", teamId)
                    put("event_type", eventType)
                    put("title", title)
                    put("body", body)
                }.toString(),
                tk
            )
            api.invokeEdgeFunction(
                "send-fcm-notification",
                buildJsonObject {
                    put("team_id", teamId)
                    put("event_type", eventType)
                    put("title", title)
                    put("body", body)
                },
                tk
            )
        }
    }
}
