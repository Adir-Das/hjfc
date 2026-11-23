package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.i18n.Formatters
import com.example.core.i18n.UiStrings
import com.example.data.repository.TeamWorkspaceSnapshot
import com.example.domain.model.MatchStatus
import com.example.domain.model.RegistrationStatus
import com.example.domain.model.UserRole
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun DashboardScreen(
    snapshot: TeamWorkspaceSnapshot,
    strings: UiStrings,
    onRefresh: () -> Unit,
    onNavigateToTab: (Int) -> Unit
) {
    val role = snapshot.currentMember.userRole
    val today = Formatters.todayDhaka().toString()
    val approvedTxns = snapshot.transactions.filter { it.approvalStatus == "APPROVED" }
    val totalBalance = snapshot.fundAccounts.sumOf { it.currentBalance }
    val totalIncome = approvedTxns.filter { it.type == "INCOME" }.sumOf { it.amount }
    val totalExpense = approvedTxns.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    val totalDues = snapshot.memberDues.sumOf { it.outstandingBalance }
    val todayCollection = approvedTxns.filter { it.type == "INCOME" && it.txnDate == today }.sumOf { it.amount }
    val todayExpense = approvedTxns.filter { it.type == "EXPENSE" && it.txnDate == today }.sumOf { it.amount }

    val upcomingMatches = snapshot.matches.filter { it.matchStatus == MatchStatus.UPCOMING }
    val pendingLeaves = snapshot.leaveRequests.count { it.status == "PENDING" }
    val pendingExpenses = snapshot.expenseRequests.count { it.status == "PENDING" }
    val pendingRegistrations = snapshot.members.count { it.regStatus == RegistrationStatus.PENDING }
    val myDue = snapshot.memberDues.firstOrNull { it.memberId == snapshot.currentMember.id }

    val monthlyBudget = snapshot.budgets.firstOrNull()?.budgetAmount ?: 0.0
    val budgetRatio = if (monthlyBudget > 0) (totalExpense / monthlyBudget).toFloat().coerceIn(0f, 1.5f) else 0f

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            BrandGradientHeader {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Image(
                            painter = painterResource(R.drawable.img_jsc_emblem),
                            contentDescription = "Club Emblem",
                            modifier = Modifier.size(48.dp).clip(CircleShape)
                        )
                        Column {
                            Text(snapshot.team.name, style = MaterialTheme.typography.titleLarge, color = Color.White)
                            Text(
                                "${snapshot.currentMember.fullName} • ${snapshot.currentMember.memberCode}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.82f)
                            )
                        }
                    }
                    IconButton(onClick = onRefresh, modifier = Modifier.testTag("dashboard_refresh_btn")) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RoleBadge(role)
                    Text(
                        "Invite: ${snapshot.team.inviteCode}",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                        color = GoldAccent
                    )
                }
            }
        }

        // Role-specific KPI Grid
        when (role) {
            UserRole.SUPER_ADMIN -> {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        MetricStatCard(
                            title = strings.currentFundBalance,
                            value = Formatters.formatBdt(totalBalance),
                            icon = Icons.Default.AccountBalanceWallet,
                            accentColor = AthleticGreen,
                            modifier = Modifier.weight(1f)
                        )
                        MetricStatCard(
                            title = strings.outstandingDues,
                            value = Formatters.formatBdt(totalDues),
                            icon = Icons.Default.ReceiptLong,
                            accentColor = StatusWarning,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        MetricStatCard(
                            title = strings.totalIncome,
                            value = Formatters.formatBdt(totalIncome),
                            icon = Icons.Default.TrendingUp,
                            accentColor = StatusInfo,
                            modifier = Modifier.weight(1f)
                        )
                        MetricStatCard(
                            title = strings.totalExpenses,
                            value = Formatters.formatBdt(totalExpense),
                            icon = Icons.Default.TrendingDown,
                            accentColor = StatusError,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        MetricStatCard(
                            title = strings.totalMembers,
                            value = snapshot.members.count { it.status == "ACTIVE" }.toString(),
                            icon = Icons.Default.Groups,
                            subtitle = "Pending Reg: $pendingRegistrations",
                            modifier = Modifier.weight(1f)
                        )
                        MetricStatCard(
                            title = strings.pendingApprovals,
                            value = "${pendingExpenses + pendingLeaves}",
                            icon = Icons.Default.Approval,
                            subtitle = "Exp: $pendingExpenses | Leave: $pendingLeaves",
                            accentColor = GoldAccent,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            UserRole.TREASURER -> {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        MetricStatCard(
                            title = strings.currentFundBalance,
                            value = Formatters.formatBdt(totalBalance),
                            icon = Icons.Default.AccountBalanceWallet,
                            accentColor = AthleticGreen,
                            modifier = Modifier.weight(1f)
                        )
                        MetricStatCard(
                            title = strings.outstandingDues,
                            value = Formatters.formatBdt(totalDues),
                            icon = Icons.Default.WarningAmber,
                            accentColor = StatusWarning,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        MetricStatCard(
                            title = "Today Collection",
                            value = Formatters.formatBdt(todayCollection),
                            icon = Icons.Default.Payments,
                            accentColor = StatusInfo,
                            modifier = Modifier.weight(1f)
                        )
                        MetricStatCard(
                            title = "Today Expenses",
                            value = Formatters.formatBdt(todayExpense),
                            icon = Icons.Default.ShoppingCartCheckout,
                            accentColor = StatusError,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            UserRole.MEMBER -> {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        MetricStatCard(
                            title = strings.personalContribution,
                            value = Formatters.formatBdt(myDue?.totalPayable ?: 0.0),
                            icon = Icons.Default.Calculate,
                            accentColor = NavyPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricStatCard(
                            title = strings.totalPaid,
                            value = Formatters.formatBdt(myDue?.totalPaid ?: 0.0),
                            icon = Icons.Default.CheckCircle,
                            accentColor = AthleticGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        MetricStatCard(
                            title = strings.outstandingDues,
                            value = Formatters.formatBdt(myDue?.outstandingBalance ?: 0.0),
                            icon = Icons.Default.Receipt,
                            accentColor = StatusWarning,
                            modifier = Modifier.weight(1f)
                        )
                        MetricStatCard(
                            title = strings.advanceBalance,
                            value = Formatters.formatBdt(myDue?.advanceBalance ?: 0.0),
                            icon = Icons.Default.Savings,
                            accentColor = StatusInfo,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Monthly Budget Progress
        if (monthlyBudget > 0) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(strings.monthlyBudget, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${Formatters.formatBdt(totalExpense)} / ${Formatters.formatBdt(monthlyBudget)}",
                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
                                color = if (budgetRatio > 1f) StatusError else AthleticGreen
                            )
                        }
                        LinearProgressIndicator(
                            progress = { budgetRatio.coerceAtMost(1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = if (budgetRatio > 1f) StatusError else AthleticGreen
                        )
                        if (budgetRatio > 1f) {
                            Text(
                                "Warning: Expenditure exceeds budget!",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusError
                            )
                        }
                    }
                }
            }
        }

        // Quick Shortcuts
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = { onNavigateToTab(2) },
                    modifier = Modifier.weight(1f).testTag("quick_matches_btn")
                ) {
                    Icon(Icons.Default.SportsSoccer, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.navMatches, maxLines = 1)
                }
                FilledTonalButton(
                    onClick = { onNavigateToTab(3) },
                    modifier = Modifier.weight(1f).testTag("quick_finance_btn")
                ) {
                    Icon(Icons.Default.Payments, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.navFinance, maxLines = 1)
                }
            }
        }

        // Upcoming Matches Preview
        item {
            SectionHeader(title = strings.upcomingMatches, actionLabel = strings.navMatches) {
                onNavigateToTab(2)
            }
        }
        if (upcomingMatches.isEmpty()) {
            item { EmptyStateCard(Icons.Default.EventAvailable, strings.noData) }
        } else {
            items(upcomingMatches.take(2), key = { it.id }) { match ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(match.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            StatusPill(match.matchDate, AthleticGreen)
                        }
                        Text("vs ${match.opponentName} • ${match.matchTime.take(5)} • ${match.venueName}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Pinned Announcements
        val pinned = snapshot.announcements.take(2)
        if (pinned.isNotEmpty()) {
            item { SectionHeader(title = strings.announcements, actionLabel = strings.navMore) { onNavigateToTab(4) } }
            items(pinned, key = { it.id }) { ann ->
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (ann.isPinned) Icon(Icons.Default.PushPin, null, tint = GoldAccent, modifier = Modifier.size(16.dp))
                            Text(ann.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        }
                        Text(ann.content, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                }
            }
        }

        // Recent Transactions
        item { SectionHeader(title = strings.recentTransactions, actionLabel = strings.navFinance) { onNavigateToTab(3) } }
        if (approvedTxns.isEmpty()) {
            item { EmptyStateCard(Icons.Default.ReceiptLong, strings.noData) }
        } else {
            items(approvedTxns.take(4), key = { it.id }) { txn ->
                ListItem(
                    headlineContent = { Text(txn.category, fontWeight = FontWeight.Medium) },
                    supportingContent = { Text("${txn.txnDate} • ${txn.description}", maxLines = 1) },
                    trailingContent = {
                        val isIncome = txn.type == "INCOME"
                        Text(
                            "${if (isIncome) "+" else "-"}${Formatters.formatBdt(txn.amount)}",
                            color = if (isIncome) AthleticGreen else StatusError,
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
                HorizontalDivider()
            }
        }
    }
}
