package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.config.AppConfig
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.Formatters
import com.example.core.preferences.SessionPreferences
import com.example.core.preferences.StoredSession
import com.example.data.remote.SupabaseClient
import com.example.data.repository.ClubRepository
import com.example.data.repository.TeamWorkspaceSnapshot
import com.example.domain.model.*
import com.example.notifications.FcmHelper
import com.example.utils.ReportExportManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ClubUiState(
    val isBootstrapping: Boolean = true,
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val session: StoredSession = StoredSession(),
    val snapshot: TeamWorkspaceSnapshot? = null,
    val errorMessage: String? = null,
    val snackbarMessage: String? = null
) {
    val language: AppLanguage get() = session.language
    val currentRole: UserRole get() = snapshot?.currentMember?.userRole ?: UserRole.MEMBER
    val isSuperAdmin: Boolean get() = currentRole == UserRole.SUPER_ADMIN
    val isTreasurerOrAdmin: Boolean get() = currentRole == UserRole.SUPER_ADMIN || currentRole == UserRole.TREASURER
}

class ClubViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = SessionPreferences(application.applicationContext)
    private val repo = ClubRepository(SupabaseClient(), prefs)

    private val _uiState = MutableStateFlow(ClubUiState())
    val uiState: StateFlow<ClubUiState> = _uiState.asStateFlow()

    private var realtimeJob: Job? = null

    init {
        FcmHelper.ensureNotificationChannel(application.applicationContext)
        viewModelScope.launch {
            prefs.sessionFlow.collect { stored ->
                val wasAuth = _uiState.value.isAuthenticated
                val isAuth = stored.accessToken.isNotBlank() && stored.userId.isNotBlank()
                _uiState.update {
                    it.copy(
                        isBootstrapping = false,
                        isAuthenticated = isAuth,
                        session = stored
                    )
                }
                if (isAuth && (!wasAuth || _uiState.value.snapshot == null)) {
                    refreshWorkspace()
                }
            }
        }
    }

    fun clearSnackbar() = _uiState.update { it.copy(snackbarMessage = null) }
    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    fun setLanguage(lang: AppLanguage) {
        viewModelScope.launch { prefs.saveLanguage(lang) }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { prefs.saveDarkMode(enabled) }
    }

    fun saveNotificationPrefs(matches: Boolean, finance: Boolean, polls: Boolean) {
        viewModelScope.launch {
            prefs.saveNotificationPrefs(matches, finance, polls)
            _uiState.update { it.copy(snackbarMessage = "Notification preferences updated") }
        }
    }

    fun signIn(email: String, password: String) {
        launchAction("Signed in successfully") {
            repo.signIn(email, password)
            refreshWorkspaceInternal()
        }
    }

    fun signUpCreateTeam(email: String, password: String, fullName: String, mobile: String, teamName: String) {
        launchAction("Club created & Super Admin ready") {
            repo.signUpAndCreateTeam(email, password, fullName, mobile, teamName)
            refreshWorkspaceInternal()
        }
    }

    fun signUpJoinInvite(email: String, password: String, fullName: String, mobile: String, inviteCode: String) {
        launchAction("Registration submitted for Admin approval") {
            repo.signUpAndJoinWithInvite(email, password, fullName, mobile, inviteCode)
            refreshWorkspaceInternal()
        }
    }

    fun forgotPassword(email: String) {
        launchAction("Password reset link sent to $email") {
            repo.forgotPassword(email)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            realtimeJob?.cancel()
            repo.signOut()
            _uiState.update { it.copy(isAuthenticated = false, snapshot = null, errorMessage = null) }
        }
    }

    fun refreshWorkspace() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { refreshWorkspaceInternal() }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message ?: "Failed to load team data") }
                }
        }
    }

    private suspend fun refreshWorkspaceInternal() {
        if (!AppConfig.isSupabaseConfigured) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        val snap = repo.loadWorkspace()
        _uiState.update { it.copy(isLoading = false, snapshot = snap, errorMessage = null) }
        startRealtime(snap.team.id, _uiState.value.session.accessToken)
        FcmHelper.fetchTokenSafely(getApplication()) { fcmToken ->
            viewModelScope.launch { repo.syncFcmToken(fcmToken) }
        }
    }

    private fun startRealtime(teamId: String, accessToken: String) {
        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            repo.observeRealtime(teamId, accessToken).collect {
                runCatching {
                    val snap = repo.loadWorkspace()
                    _uiState.update { it.copy(snapshot = snap) }
                }
            }
        }
    }

    // ---- Member Actions ----

    fun addMember(fullName: String, email: String, mobile: String, whatsapp: String, dob: String?, size: String, pos: String, role: UserRole) {
        val snap = _uiState.value.snapshot ?: return
        val nextCode = "JSC-${(snap.members.size + 1).toString().padStart(3, '0')}"
        launchAction("Member added ($nextCode)") {
            repo.addMember(snap.team.id, fullName, email, mobile, whatsapp, dob, size, pos, role, nextCode)
            refreshWorkspaceInternal()
        }
    }

    fun updateMemberRoleStatus(memberId: String, role: UserRole, status: MemberStatus, regStatus: RegistrationStatus) {
        launchAction("Member updated") {
            repo.updateMemberStatusAndRole(memberId, role, status, regStatus)
            refreshWorkspaceInternal()
        }
    }

    fun updateOwnProfile(fullName: String, mobile: String, whatsapp: String, dob: String?, size: String, pos: String, emergency: String?) {
        val member = _uiState.value.snapshot?.currentMember ?: return
        launchAction("Profile saved") {
            repo.updateOwnProfile(member.id, fullName, mobile, whatsapp, dob, size, pos, emergency)
            refreshWorkspaceInternal()
        }
    }

    // ---- Availability & Matches ----

    fun submitAvailability(memberId: String, dates: List<String>, status: AvailabilityStatus, remarks: String?, isAdminOverride: Boolean = false) {
        val snap = _uiState.value.snapshot ?: return
        val overrideId = if (isAdminOverride) _uiState.value.session.userId else null
        launchAction("Availability updated (${dates.size} day(s))") {
            repo.setAvailabilityDates(snap.team.id, memberId, dates, status, remarks, overrideId)
            refreshWorkspaceInternal()
        }
    }

    fun createMatch(title: String, opponent: String, date: String, time: String, venue: String, mapsUrl: String?, desc: String?, budget: Double) {
        val snap = _uiState.value.snapshot ?: return
        launchAction("Match published") {
            repo.createMatch(snap.team.id, _uiState.value.session.userId, title, opponent, date, time, venue, mapsUrl, desc, budget)
            refreshWorkspaceInternal()
        }
    }

    fun updateMatchStatus(matchId: String, status: MatchStatus, newDate: String? = null) {
        launchAction("Match status updated") {
            repo.updateMatchStatus(matchId, status, newDate)
            refreshWorkspaceInternal()
        }
    }

    fun recordAttendance(gameId: String, memberId: String, status: AttendanceStatus, reason: String?) {
        val snap = _uiState.value.snapshot ?: return
        launchAction("Attendance recorded & dues recalculated") {
            repo.recordAttendance(snap.team.id, gameId, memberId, status, reason, _uiState.value.session.userId)
            refreshWorkspaceInternal()
        }
    }

    fun submitLeaveRequest(date: String, reason: String, note: String?) {
        val snap = _uiState.value.snapshot ?: return
        launchAction("Leave application submitted") {
            repo.submitLeaveRequest(snap.team.id, snap.currentMember.id, date, reason, note)
            refreshWorkspaceInternal()
        }
    }

    fun reviewLeaveRequest(requestId: String, decision: String, remarks: String) {
        launchAction("Leave application $decision") {
            repo.reviewLeaveRequest(requestId, decision, remarks, _uiState.value.session.userId)
            refreshWorkspaceInternal()
        }
    }

    fun saveMatchResult(gameId: String, opponent: String, matchDate: String, teamScore: Int, oppScore: Int, motmId: String?, notes: String?) {
        val snap = _uiState.value.snapshot ?: return
        launchAction("Match result saved") {
            repo.saveMatchResult(snap.team.id, gameId, opponent, matchDate, teamScore, oppScore, motmId, notes, _uiState.value.session.userId)
            refreshWorkspaceInternal()
        }
    }

    // ---- Finance & Reports ----

    fun createContributionRule(name: String, ruleType: String, amount: Double, targetMemberId: String?) {
        val snap = _uiState.value.snapshot ?: return
        launchAction("Contribution rule created") {
            repo.createContributionRule(snap.team.id, name, ruleType, amount, targetMemberId, _uiState.value.session.userId)
            repo.recalculateAllMemberDues(snap.team.id, snap.members)
            refreshWorkspaceInternal()
        }
    }

    fun recordMemberPayment(memberId: String, amount: Double, accountId: String, method: String, isAdvance: Boolean, desc: String) {
        val snap = _uiState.value.snapshot ?: return
        launchAction("Payment recorded in BDT") {
            repo.recordMemberPayment(snap.team.id, memberId, amount, accountId, method, isAdvance, desc)
            refreshWorkspaceInternal()
        }
    }

    fun recordIncome(category: String, amount: Double, accountId: String, method: String, desc: String) {
        val snap = _uiState.value.snapshot ?: return
        launchAction("Income recorded") {
            repo.recordDirectIncome(snap.team.id, category, amount, accountId, method, desc, _uiState.value.session.userId)
            refreshWorkspaceInternal()
        }
    }

    fun submitExpenseRequest(amount: Double, category: String, desc: String, gameId: String?, accountId: String?) {
        val snap = _uiState.value.snapshot ?: return
        launchAction("Expense request submitted for Super Admin approval") {
            repo.submitExpenseRequest(snap.team.id, amount, category, desc, gameId, accountId, _uiState.value.session.userId)
            refreshWorkspaceInternal()
        }
    }

    fun reviewExpenseRequest(requestId: String, decision: String, remarks: String) {
        launchAction("Expense $decision") {
            repo.reviewExpenseRequest(requestId, decision, remarks)
            refreshWorkspaceInternal()
        }
    }

    fun executeFundTransfer(fromId: String, toId: String, amount: Double, notes: String) {
        val snap = _uiState.value.snapshot ?: return
        launchAction("Fund transfer completed") {
            repo.executeFundTransfer(snap.team.id, fromId, toId, amount, notes)
            refreshWorkspaceInternal()
        }
    }

    fun createBudgetOrGoal(isBudget: Boolean, title: String, amount: Double, periodOrDeadline: String) {
        val snap = _uiState.value.snapshot ?: return
        launchAction(if (isBudget) "Budget saved" else "Fundraising goal saved") {
            repo.createBudgetOrGoal(snap.team.id, isBudget, title, amount, periodOrDeadline, _uiState.value.session.userId)
            refreshWorkspaceInternal()
        }
    }

    fun exportAndShareMonthlyReport(context: Context, isPdf: Boolean) {
        val snap = _uiState.value.snapshot ?: return
        val period = Formatters.todayDhaka().toString().substring(0, 7)
        launchAction(if (isPdf) "PDF Report ready" else "Excel Report ready") {
            val approved = snap.transactions.filter { it.approvalStatus == "APPROVED" }
            val contrib = approved.filter { it.type == "INCOME" && it.category.contains("Contribution", true) }.sumOf { it.amount }
            val other = approved.filter { it.type == "INCOME" && !it.category.contains("Contribution", true) }.sumOf { it.amount }
            val exp = approved.filter { it.type == "EXPENSE" }.sumOf { it.amount }
            val opening = snap.fundAccounts.sumOf { it.openingBalance }
            val closing = snap.fundAccounts.sumOf { it.currentBalance }
            val dues = snap.memberDues.sumOf { it.outstandingBalance }
            val adv = snap.memberDues.sumOf { it.advanceBalance }

            repo.saveMonthlyReportRecord(snap.team.id, period, opening, contrib, other, exp, closing, dues, adv, _uiState.value.session.userId)
            if (isPdf) {
                val file = ReportExportManager.generateMonthlyPdf(context, snap, period)
                ReportExportManager.shareFile(context, file, "application/pdf", "${snap.team.name} Report ($period)")
            } else {
                val file = ReportExportManager.generateExcelSpreadsheet(context, snap, period)
                ReportExportManager.shareFile(context, file, "application/vnd.ms-excel", "${snap.team.name} Ledger ($period)")
            }
            refreshWorkspaceInternal()
        }
    }

    // ---- Announcements, Polls, Gallery, Equipment, WhatsApp, Settings ----

    fun publishAnnouncement(title: String, content: String, isPinned: Boolean) {
        val snap = _uiState.value.snapshot ?: return
        launchAction("Announcement published") {
            repo.publishAnnouncement(snap.team.id, title, content, isPinned, _uiState.value.session.userId)
            refreshWorkspaceInternal()
        }
    }

    fun createGeneralPoll(title: String, category: String, options: List<String>, isAnonymous: Boolean, daysOpen: Int) {
        val snap = _uiState.value.snapshot ?: return
        val deadline = java.time.ZonedDateTime.now(AppConfig.DHAKA_ZONE).plusDays(daysOpen.toLong()).format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        launchAction("Team poll published") {
            repo.createGeneralPoll(snap.team.id, title, category, options, isAnonymous, deadline, _uiState.value.session.userId)
            refreshWorkspaceInternal()
        }
    }

    fun castVote(pollId: String, optionId: String) {
        val snap = _uiState.value.snapshot ?: return
        launchAction("Vote recorded") {
            repo.castPollVote(snap.team.id, pollId, optionId, snap.currentMember.id)
            refreshWorkspaceInternal()
        }
    }

    fun uploadGalleryMedia(context: Context, uri: Uri, mediaType: String, caption: String) {
        val snap = _uiState.value.snapshot ?: return
        launchAction("Media uploaded to Supabase Storage") {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Cannot read selected file")
            val mime = context.contentResolver.getType(uri) ?: if (mediaType == "VIDEO") "video/mp4" else "image/jpeg"
            val ext = if (mime.contains("video")) "mp4" else if (mime.contains("png")) "png" else "jpg"
            repo.uploadGalleryMedia(snap.team.id, mediaType, bytes, mime, ext, caption, _uiState.value.session.userId)
            refreshWorkspaceInternal()
        }
    }

    fun addEquipmentOrJersey(isJersey: Boolean, nameOrSize: String, categoryOrNum: String, qtyOrMemberId: String, cost: Double, location: String) {
        val snap = _uiState.value.snapshot ?: return
        launchAction(if (isJersey) "Jersey saved" else "Equipment added") {
            repo.addEquipmentOrJersey(snap.team.id, isJersey, nameOrSize, categoryOrNum, qtyOrMemberId, cost, location)
            refreshWorkspaceInternal()
        }
    }

    fun checkoutEquipment(item: EquipmentItem, memberId: String, qty: Int) {
        val snap = _uiState.value.snapshot ?: return
        launchAction("Equipment checked out") {
            repo.checkoutEquipment(snap.team.id, item, memberId, qty, _uiState.value.session.userId)
            refreshWorkspaceInternal()
        }
    }

    fun returnOrReportEquipment(assignment: EquipmentAssignment, status: String) {
        val snap = _uiState.value.snapshot ?: return
        val eq = snap.equipment.firstOrNull { it.id == assignment.equipmentId }
        launchAction("Equipment status updated: $status") {
            repo.returnOrReportEquipment(assignment, eq, status)
            refreshWorkspaceInternal()
        }
    }

    fun sendWhatsAppAutomation(phone: String, memberId: String?, template: String, preview: String) {
        val snap = _uiState.value.snapshot ?: return
        launchAction("WhatsApp dispatch logged") {
            repo.sendWhatsAppViaEdge(snap.team.id, phone, memberId, template, preview)
            refreshWorkspaceInternal()
        }
    }

    fun logManualWhatsAppShare(phone: String, preview: String) {
        val snap = _uiState.value.snapshot ?: return
        viewModelScope.launch {
            runCatching {
                repo.logManualWhatsAppShare(snap.team.id, phone, preview)
                refreshWorkspaceInternal()
            }
        }
    }

    fun updateTeamSettings(whatsappEnabled: Boolean, allowViewOthers: Boolean, deadlineHours: Int) {
        val snap = _uiState.value.snapshot ?: return
        launchAction("Team settings saved") {
            repo.updateTeamSettings(snap.team.id, whatsappEnabled, allowViewOthers, deadlineHours, _uiState.value.session.userId)
            refreshWorkspaceInternal()
        }
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch {
            runCatching {
                repo.markNotificationRead(id)
                refreshWorkspaceInternal()
            }
        }
    }

    private fun launchAction(successMsg: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { block() }
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, snackbarMessage = successMsg) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            snackbarMessage = err.message ?: "Operation failed"
                        )
                    }
                }
        }
    }
}
