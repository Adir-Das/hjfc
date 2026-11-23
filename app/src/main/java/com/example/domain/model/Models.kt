package com.example.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

enum class UserRole {
    SUPER_ADMIN, TREASURER, MEMBER;

    companion object {
        fun from(value: String?): UserRole =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MEMBER
    }
}

enum class MemberStatus {
    ACTIVE, INACTIVE, SUSPENDED, FORMER_MEMBER;

    companion object {
        fun from(value: String?): MemberStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ACTIVE
    }
}

enum class RegistrationStatus {
    PENDING, APPROVED, REJECTED;

    companion object {
        fun from(value: String?): RegistrationStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: APPROVED
    }
}

enum class AvailabilityStatus {
    AVAILABLE, UNAVAILABLE, NOT_RESPONDED;

    companion object {
        fun from(value: String?): AvailabilityStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NOT_RESPONDED
    }
}

enum class AttendanceStatus {
    PRESENT, ABSENT, LATE, EXCUSED;

    companion object {
        fun from(value: String?): AttendanceStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ABSENT
    }
}

enum class MatchStatus {
    UPCOMING, COMPLETED, POSTPONED, CANCELLED;

    companion object {
        fun from(value: String?): MatchStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UPCOMING
    }
}

@Serializable
data class AuthUserDto(
    val id: String = "",
    val email: String? = null
)

@Serializable
data class AuthResponseDto(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    val user: AuthUserDto? = null
)

@Serializable
data class Team(
    val id: String = "",
    val name: String = "",
    @SerialName("short_name") val shortName: String = "JSC",
    @SerialName("invite_code") val inviteCode: String = "",
    val timezone: String = "Asia/Dhaka",
    @SerialName("currency_symbol") val currencySymbol: String = "৳",
    @SerialName("logo_url") val logoUrl: String? = null
)

@Serializable
data class TeamSettings(
    val id: String = "",
    @SerialName("team_id") val teamId: String = "",
    @SerialName("availability_deadline_hours_before") val availabilityDeadlineHoursBefore: Int = 18,
    @SerialName("first_reminder_hours_before") val firstReminderHoursBefore: Int = 40,
    @SerialName("final_reminder_hours_before") val finalReminderHoursBefore: Int = 24,
    @SerialName("allow_members_view_others_availability") val allowMembersViewOthersAvailability: Boolean = true,
    @SerialName("allow_member_availability_edit_before_deadline") val allowMemberAvailabilityEditBeforeDeadline: Boolean = true,
    @SerialName("expense_approval_limit_bdt") val expenseApprovalLimitBdt: Double = 5000.0,
    @SerialName("whatsapp_enabled") val whatsappEnabled: Boolean = false,
    @SerialName("whatsapp_daily_report_time") val whatsappDailyReportTime: String = "21:00",
    @SerialName("default_language") val defaultLanguage: String = "bn"
)

@Serializable
data class TeamMember(
    val id: String = "",
    @SerialName("team_id") val teamId: String = "",
    @SerialName("user_id") val userId: String? = null,
    @SerialName("member_code") val memberCode: String = "",
    @SerialName("full_name") val fullName: String = "",
    val email: String = "",
    @SerialName("mobile_number") val mobileNumber: String = "",
    @SerialName("whatsapp_number") val whatsappNumber: String = "",
    @SerialName("profile_picture_url") val profilePictureUrl: String? = null,
    @SerialName("date_of_birth") val dateOfBirth: String? = null,
    @SerialName("joining_date") val joiningDate: String = "",
    @SerialName("jersey_size") val jerseySize: String = "L",
    @SerialName("preferred_position") val preferredPosition: String = "Midfielder",
    @SerialName("emergency_contact") val emergencyContact: String? = null,
    val role: String = "MEMBER",
    val status: String = "ACTIVE",
    @SerialName("registration_status") val registrationStatus: String = "APPROVED",
    @SerialName("whatsapp_opt_in") val whatsappOptIn: Boolean = true
) {
    val userRole: UserRole get() = UserRole.from(role)
    val memberStatus: MemberStatus get() = MemberStatus.from(status)
    val regStatus: RegistrationStatus get() = RegistrationStatus.from(registrationStatus)
}

@Serializable
data class MatchGame(
    val id: String = "",
    @SerialName("team_id") val teamId: String = "",
    val title: String = "",
    @SerialName("opponent_name") val opponentName: String = "",
    @SerialName("match_date") val matchDate: String = "",
    @SerialName("match_time") val matchTime: String = "16:00:00",
    @SerialName("venue_name") val venueName: String = "",
    @SerialName("maps_url") val mapsUrl: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val description: String? = null,
    @SerialName("expected_budget") val expectedBudget: Double = 0.0,
    @SerialName("availability_deadline") val availabilityDeadline: String? = null,
    val status: String = "UPCOMING",
    @SerialName("is_published") val isPublished: Boolean = true,
    @SerialName("created_by") val createdBy: String = ""
) {
    val matchStatus: MatchStatus get() = MatchStatus.from(status)
}

@Serializable
data class AvailabilityRecord(
    val id: String = "",
    @SerialName("team_id") val teamId: String = "",
    @SerialName("member_id") val memberId: String = "",
    @SerialName("avail_date") val availDate: String = "",
    @SerialName("game_id") val gameId: String? = null,
    val status: String = "NOT_RESPONDED",
    val remarks: String? = null,
    @SerialName("overridden_by") val overriddenBy: String? = null,
    @SerialName("updated_at") val updatedAt: String = ""
) {
    val availabilityStatus: AvailabilityStatus get() = AvailabilityStatus.from(status)
}

@Serializable
data class AttendanceRecord(
    val id: String = "",
    @SerialName("team_id") val teamId: String = "",
    @SerialName("game_id") val gameId: String = "",
    @SerialName("member_id") val memberId: String = "",
    val status: String = "ABSENT",
    @SerialName("absence_reason") val absenceReason: String? = null,
    @SerialName("recorded_by") val recordedBy: String = "",
    @SerialName("created_at") val createdAt: String = ""
) {
    val attendanceStatus: AttendanceStatus get() = AttendanceStatus.from(status)
}

@Serializable
data class LeaveRequest(
    val id: String = "",
    @SerialName("team_id") val teamId: String = "",
    @SerialName("member_id") val memberId: String = "",
    @SerialName("leave_date") val leaveDate: String = "",
    val reason: String = "",
    @SerialName("attachment_url") val attachmentUrl: String? = null,
    @SerialName("additional_note") val additionalNote: String? = null,
    val status: String = "PENDING",
    @SerialName("admin_remarks") val adminRemarks: String? = null,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class ContributionRule(
    val id: String = "",
    @SerialName("team_id") val teamId: String = "",
    val name: String = "",
    @SerialName("rule_type") val ruleType: String = "ATTENDANCE_BASED",
    val amount: Double = 0.0,
    @SerialName("game_id") val gameId: String? = null,
    @SerialName("target_member_id") val targetMemberId: String? = null,
    @SerialName("effective_from") val effectiveFrom: String = "",
    @SerialName("is_active") val isActive: Boolean = true
)

@Serializable
data class MemberDue(
    val id: String = "",
    @SerialName("team_id") val teamId: String = "",
    @SerialName("member_id") val memberId: String = "",
    @SerialName("total_payable") val totalPayable: Double = 0.0,
    @SerialName("total_paid") val totalPaid: Double = 0.0,
    @SerialName("applied_advance") val appliedAdvance: Double = 0.0,
    @SerialName("advance_balance") val advanceBalance: Double = 0.0,
    @SerialName("discount_amount") val discountAmount: Double = 0.0,
    @SerialName("additional_charges") val additionalCharges: Double = 0.0,
    @SerialName("outstanding_balance") val outstandingBalance: Double = 0.0,
    @SerialName("last_calculated_at") val lastCalculatedAt: String = ""
)

@Serializable
data class FundAccount(
    val id: String = "",
    @SerialName("team_id") val teamId: String = "",
    val name: String = "",
    @SerialName("account_type") val accountType: String = "CASH",
    @SerialName("account_number") val accountNumber: String? = null,
    @SerialName("opening_balance") val openingBalance: Double = 0.0,
    @SerialName("current_balance") val currentBalance: Double = 0.0,
    @SerialName("is_active") val isActive: Boolean = true
)

@Serializable
data class FinancialTransaction(
    val id: String = "",
    @SerialName("team_id") val teamId: String = "",
    @SerialName("transaction_ref") val transactionRef: String = "",
    val type: String = "INCOME",
    val category: String = "",
    val amount: Double = 0.0,
    @SerialName("txn_date") val txnDate: String = "",
    val description: String = "",
    @SerialName("related_member_id") val relatedMemberId: String? = null,
    @SerialName("related_game_id") val relatedGameId: String? = null,
    @SerialName("fund_account_id") val fundAccountId: String? = null,
    @SerialName("payment_method") val paymentMethod: String = "CASH",
    @SerialName("receipt_url") val receiptUrl: String? = null,
    @SerialName("is_advance") val isAdvance: Boolean = false,
    @SerialName("approval_status") val approvalStatus: String = "APPROVED",
    @SerialName("created_by") val createdBy: String = "",
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class FundTransfer(
    val id: String = "",
    @SerialName("team_id") val teamId: String = "",
    @SerialName("from_account_id") val fromAccountId: String = "",
    @SerialName("to_account_id") val toAccountId: String = "",
    val amount: Double = 0.0,
    @SerialName("transfer_date") val transferDate: String = "",
    @SerialName("reference_code") val referenceCode: String = "",
    val notes: String? = null
)

@Serializable
data class ExpenseRequest(
    val id: String = "",
    @SerialName("team_id") val teamId: String = "",
    @SerialName("requested_amount") val requestedAmount: Double = 0.0,
    val category: String = "",
    val description: String = "",
    @SerialName("related_game_id") val relatedGameId: String? = null,
    @SerialName("fund_account_id") val fundAccountId: String? = null,
    @SerialName("receipt_url") val receiptUrl: String? = null,
    @SerialName("submission_date") val submissionDate: String = "",
    val status: String = "PENDING",
    @SerialName("approval_remarks") val approvalRemarks: String? = null,
    @SerialName("requested_by") val requestedBy: String = ""
)

@Serializable
data class BudgetItem(
    val id: String = "",
    @SerialName("team_id") val teamId: String = "",
    val title: String = "",
    @SerialName("budget_type") val budgetType: String = "MONTHLY",
    @SerialName("period_key") val periodKey: String = "",
    @SerialName("game_id") val gameId: String? = null,
    @SerialName("budget_amount") val budgetAmount: Double = 0.0
)

@Serializable
data class FundGoal(
    val id: String = "",
    @SerialName("team_id") val teamId: String = "",
    val title: String = "",
    @SerialName("goal_type") val goalType: String = "CONTRIBUTION_TARGET",
    @SerialName("target_amount") val targetAmount: Double = 0.0,
    @SerialName("collected_amount") val collectedAmount: Double = 0.0,
    val deadline: String? = null,
    @SerialName("is_completed") val isCompleted: Boolean = false
)

@Serializable
data class NotificationItem(
    val id: String = "",
    @SerialName("team_id") val teamId: String = "",
    @SerialName("user_id") val userId: String? = null,
    @SerialName("event_type") val eventType: String = "",
    val title: String = "",
    val body: String = "",
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class Announcement(
    val id: String = "",
    @SerialName("team_id") val teamId: String = "",
    val title: String = "",
    val content: String = "",
    @SerialName("is_pinned") val isPinned: Boolean = false,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("attachment_url") val attachmentUrl: String? = null,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class TeamPoll(
    val id: String = "",
    @SerialName("team_id") val teamId: String = "",
    val title: String = "",
    val description: String? = null,
    @SerialName("poll_category") val pollCategory: String = "GENERAL",
    @SerialName("related_game_id") val relatedGameId: String? = null,
    @SerialName("is_anonymous") val isAnonymous: Boolean = false,
    val deadline: String = "",
    @SerialName("is_closed") val isClosed: Boolean = false,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class PollOption(
    val id: String = "",
    @SerialName("team_id") val teamId: String = "",
    @SerialName("poll_id") val pollId: String = "",
    @SerialName("option_text") val optionText: String = "",
    @SerialName("linked_member_id") val linkedMemberId: String? = null
)

@Serializable
data class PollVote(
    val id: String = "",
    @SerialName("team_id") val teamId: String = "",
    @SerialName("poll_id") val pollId: String = "",
    @SerialName("option_id") val optionId: String = "",
    @SerialName("member_id") val memberId: String = ""
)

@Serializable
data class GalleryAlbum(
    val id: String = "",
    @SerialName("team_id") val teamId: String = "",
    val title: String = "",
    @SerialName("game_id") val gameId: String? = null,
    @SerialName("album_date") val albumDate: String = ""
)

@Serializable
data class GalleryItem(
    val id: String = "",
    @SerialName("team_id") val teamId: String = "",
    @SerialName("album_id") val albumId: String? = null,
    @SerialName("game_id") val gameId: String? = null,
    @SerialName("media_type") val mediaType: String = "IMAGE",
    @SerialName("media_url") val mediaUrl: String = "",
    val caption: String? = null,
    @SerialName("is_archived") val isArchived: Boolean = false,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class MatchResult(
    val id: String = "",
    @SerialName("team_id") val teamId: String = "",
    @SerialName("game_id") val gameId: String = "",
    @SerialName("opponent_name") val opponentName: String = "",
    @SerialName("match_date") val matchDate: String = "",
    @SerialName("team_score") val teamScore: Int = 0,
    @SerialName("opponent_score") val opponentScore: Int = 0,
    val outcome: String = "DRAW",
    @SerialName("man_of_the_match_member_id") val manOfTheMatchMemberId: String? = null,
    @SerialName("motm_selection_method") val motmSelectionMethod: String = "SUPER_ADMIN",
    @SerialName("match_notes") val matchNotes: String? = null
)

@Serializable
data class PlayerMatchStat(
    val id: String = "",
    @SerialName("team_id") val teamId: String = "",
    @SerialName("game_id") val gameId: String = "",
    @SerialName("member_id") val memberId: String = "",
    val goals: Int = 0,
    val assists: Int = 0,
    @SerialName("is_player_of_match") val isPlayerOfMatch: Boolean = false
)

@Serializable
data class EquipmentItem(
    val id: String = "",
    @SerialName("team_id") val teamId: String = "",
    val name: String = "",
    val category: String = "",
    val quantity: Int = 1,
    @SerialName("available_quantity") val availableQuantity: Int = 1,
    @SerialName("low_stock_threshold") val lowStockThreshold: Int = 2,
    @SerialName("purchase_date") val purchaseDate: String? = null,
    @SerialName("purchase_cost") val purchaseCost: Double = 0.0,
    val condition: String = "GOOD",
    @SerialName("storage_location") val storageLocation: String = "Club Room",
    @SerialName("responsible_member_id") val responsibleMemberId: String? = null
)

@Serializable
data class EquipmentAssignment(
    val id: String = "",
    @SerialName("team_id") val teamId: String = "",
    @SerialName("equipment_id") val equipmentId: String = "",
    @SerialName("member_id") val memberId: String = "",
    val quantity: Int = 1,
    @SerialName("checkout_date") val checkoutDate: String = "",
    @SerialName("return_date") val returnDate: String? = null,
    val status: String = "CHECKED_OUT",
    val notes: String? = null
)

@Serializable
data class JerseyItem(
    val id: String = "",
    @SerialName("team_id") val teamId: String = "",
    @SerialName("jersey_number") val jerseyNumber: Int = 0,
    val size: String = "L",
    @SerialName("assigned_member_id") val assignedMemberId: String? = null,
    @SerialName("issue_date") val issueDate: String? = null,
    @SerialName("return_date") val returnDate: String? = null,
    val condition: String = "GOOD"
)

@Serializable
data class WhatsAppLog(
    val id: String = "",
    @SerialName("team_id") val teamId: String = "",
    @SerialName("recipient_phone") val recipientPhone: String = "",
    @SerialName("template_name") val templateName: String = "",
    @SerialName("message_preview") val messagePreview: String = "",
    @SerialName("delivery_status") val deliveryStatus: String = "QUEUED",
    @SerialName("retry_count") val retryCount: Int = 0,
    @SerialName("error_message") val errorMessage: String? = null,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class MonthlyReportRecord(
    val id: String = "",
    @SerialName("team_id") val teamId: String = "",
    @SerialName("report_period") val reportPeriod: String = "",
    @SerialName("opening_balance") val openingBalance: Double = 0.0,
    @SerialName("total_contributions") val totalContributions: Double = 0.0,
    @SerialName("other_income") val otherIncome: Double = 0.0,
    @SerialName("total_expenses") val totalExpenses: Double = 0.0,
    @SerialName("closing_balance") val closingBalance: Double = 0.0,
    @SerialName("outstanding_dues") val outstandingDues: Double = 0.0,
    @SerialName("advance_payments") val advancePayments: Double = 0.0,
    @SerialName("generated_at") val generatedAt: String = ""
)

@Serializable
data class AuditLogEntry(
    val id: String = "",
    @SerialName("team_id") val teamId: String = "",
    @SerialName("actor_user_id") val actorUserId: String? = null,
    @SerialName("action_type") val actionType: String = "",
    @SerialName("entity_type") val entityType: String = "",
    @SerialName("entity_id") val entityId: String = "",
    @SerialName("previous_values") val previousValues: JsonElement? = null,
    @SerialName("new_values") val newValues: JsonElement? = null,
    val remarks: String? = null,
    @SerialName("created_at") val createdAt: String = ""
)
