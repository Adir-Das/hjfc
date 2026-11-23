package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.i18n.Formatters
import com.example.core.i18n.UiStrings
import com.example.data.repository.TeamWorkspaceSnapshot
import com.example.domain.model.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.ClubViewModel

@Composable
fun FinanceScreen(
    snapshot: TeamWorkspaceSnapshot,
    strings: UiStrings,
    viewModel: ClubViewModel
) {
    val context = LocalContext.current
    val isAdmin = snapshot.currentMember.userRole == UserRole.SUPER_ADMIN
    val isTreasurerOrAdmin = snapshot.currentMember.userRole != UserRole.MEMBER
    var subTab by remember { mutableIntStateOf(0) }

    var showPaymentDialog by remember { mutableStateOf(false) }
    var showIncomeDialog by remember { mutableStateOf(false) }
    var showExpenseReqDialog by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var showRuleDialog by remember { mutableStateOf(false) }
    var showBudgetDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = subTab, edgePadding = 12.dp) {
            Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text(strings.cashbook) })
            Tab(selected = subTab == 1, onClick = { subTab = 1 }, text = { Text(strings.contributionRules) })
            Tab(selected = subTab == 2, onClick = { subTab = 2 }, text = { Text("Expense Approvals") })
            Tab(selected = subTab == 3, onClick = { subTab = 3 }, text = { Text(strings.budgetsAndGoals) })
        }

        when (subTab) {
            0 -> CashbookPane(
                snapshot = snapshot,
                strings = strings,
                isTreasurerOrAdmin = isTreasurerOrAdmin,
                onRecordPayment = { showPaymentDialog = true },
                onRecordIncome = { showIncomeDialog = true },
                onTransfer = { showTransferDialog = true }
            )
            1 -> DuesAndRulesPane(
                snapshot = snapshot,
                strings = strings,
                isAdmin = isAdmin,
                isTreasurerOrAdmin = isTreasurerOrAdmin,
                onAddRule = { showRuleDialog = true },
                onRecordPayment = { showPaymentDialog = true }
            )
            2 -> ExpenseApprovalPane(
                snapshot = snapshot,
                strings = strings,
                isAdmin = isAdmin,
                isTreasurerOrAdmin = isTreasurerOrAdmin,
                onSubmitExpense = { showExpenseReqDialog = true },
                onReview = { id, decision, remarks -> viewModel.reviewExpenseRequest(id, decision, remarks) }
            )
            3 -> BudgetsAndReportsPane(
                snapshot = snapshot,
                strings = strings,
                isAdmin = isAdmin,
                onAddBudgetGoal = { showBudgetDialog = true },
                onExportPdf = { viewModel.exportAndShareMonthlyReport(context, isPdf = true) },
                onExportExcel = { viewModel.exportAndShareMonthlyReport(context, isPdf = false) }
            )
        }
    }

    if (showPaymentDialog) {
        RecordPaymentDialog(
            members = snapshot.members.filter { it.status == "ACTIVE" },
            accounts = snapshot.fundAccounts,
            onDismiss = { showPaymentDialog = false },
            onSave = { mId, amt, accId, method, isAdv, desc ->
                viewModel.recordMemberPayment(mId, amt, accId, method, isAdv, desc)
                showPaymentDialog = false
            }
        )
    }

    if (showIncomeDialog) {
        RecordIncomeDialog(
            accounts = snapshot.fundAccounts,
            onDismiss = { showIncomeDialog = false },
            onSave = { cat, amt, accId, method, desc ->
                viewModel.recordIncome(cat, amt, accId, method, desc)
                showIncomeDialog = false
            }
        )
    }

    if (showExpenseReqDialog) {
        SubmitExpenseRequestDialog(
            accounts = snapshot.fundAccounts,
            games = snapshot.matches,
            onDismiss = { showExpenseReqDialog = false },
            onSubmit = { amt, cat, desc, gameId, accId ->
                viewModel.submitExpenseRequest(amt, cat, desc, gameId, accId)
                showExpenseReqDialog = false
            }
        )
    }

    if (showTransferDialog) {
        FundTransferDialog(
            accounts = snapshot.fundAccounts,
            onDismiss = { showTransferDialog = false },
            onTransfer = { fromId, toId, amt, notes ->
                viewModel.executeFundTransfer(fromId, toId, amt, notes)
                showTransferDialog = false
            }
        )
    }

    if (showRuleDialog) {
        ContributionRuleDialog(
            members = snapshot.members,
            onDismiss = { showRuleDialog = false },
            onSave = { name, type, amt, memberId ->
                viewModel.createContributionRule(name, type, amt, memberId)
                showRuleDialog = false
            }
        )
    }

    if (showBudgetDialog) {
        BudgetOrGoalDialog(
            onDismiss = { showBudgetDialog = false },
            onSave = { isBudget, title, amt, period ->
                viewModel.createBudgetOrGoal(isBudget, title, amt, period)
                showBudgetDialog = false
            }
        )
    }
}

@Composable
private fun CashbookPane(
    snapshot: TeamWorkspaceSnapshot,
    strings: UiStrings,
    isTreasurerOrAdmin: Boolean,
    onRecordPayment: () -> Unit,
    onRecordIncome: () -> Unit,
    onTransfer: () -> Unit
) {
    val approved = snapshot.transactions.filter { it.approvalStatus == "APPROVED" }
    val opening = snapshot.fundAccounts.sumOf { it.openingBalance }
    val totalIncome = approved.filter { it.type == "INCOME" }.sumOf { it.amount }
    val totalExpense = approved.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    val currentBal = snapshot.fundAccounts.sumOf { it.currentBalance }
    val pendingExpTotal = snapshot.expenseRequests.filter { it.status == "PENDING" }.sumOf { it.requestedAmount }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(strings.currentFundBalance, style = MaterialTheme.typography.labelMedium)
                    Text(
                        Formatters.formatBdt(currentBal),
                        style = MaterialTheme.typography.displaySmall.copy(fontFamily = MonoFontFamily),
                        color = AthleticGreen
                    )
                    Text(
                        "Opening: ${Formatters.formatBdt(opening)} | +Income: ${Formatters.formatBdt(totalIncome)} | -Expense: ${Formatters.formatBdt(totalExpense)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Pending Expenses (Excluded): ${Formatters.formatBdt(pendingExpTotal)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusWarning
                    )
                }
            }
        }

        if (isTreasurerOrAdmin) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onRecordPayment, modifier = Modifier.weight(1f).testTag("record_payment_btn")) {
                        Text("Pay Due", maxLines = 1)
                    }
                    FilledTonalButton(onClick = onRecordIncome, modifier = Modifier.weight(1f).testTag("record_income_btn")) {
                        Text("+Income", maxLines = 1)
                    }
                    OutlinedButton(onClick = onTransfer, modifier = Modifier.weight(1f).testTag("fund_transfer_btn")) {
                        Text("Transfer", maxLines = 1)
                    }
                }
            }
        }

        item { SectionHeader(title = strings.fundAccounts) }
        items(snapshot.fundAccounts, key = { it.id }) { acc ->
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(acc.name, fontWeight = FontWeight.SemiBold)
                        Text(acc.accountType, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        Formatters.formatBdt(acc.currentBalance),
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = NavyPrimary
                    )
                }
            }
        }

        item { SectionHeader(title = strings.recentTransactions) }
        if (snapshot.transactions.isEmpty()) {
            item { EmptyStateCard(Icons.Default.AccountBalance, strings.noData) }
        } else {
            items(snapshot.transactions, key = { it.id }) { txn ->
                ListItem(
                    headlineContent = { Text("${txn.category} (${txn.transactionRef})", fontWeight = FontWeight.Medium) },
                    supportingContent = { Text("${txn.txnDate} • ${txn.paymentMethod} • ${txn.description}") },
                    trailingContent = {
                        val isInc = txn.type == "INCOME"
                        Text(
                            "${if (isInc) "+" else "-"}${Formatters.formatBdt(txn.amount)}",
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = if (isInc) AthleticGreen else StatusError
                        )
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun DuesAndRulesPane(
    snapshot: TeamWorkspaceSnapshot,
    strings: UiStrings,
    isAdmin: Boolean,
    isTreasurerOrAdmin: Boolean,
    onAddRule: () -> Unit,
    onRecordPayment: () -> Unit
) {
    val visibleDues = if (isTreasurerOrAdmin) {
        snapshot.memberDues
    } else {
        snapshot.memberDues.filter { it.memberId == snapshot.currentMember.id }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(
                title = strings.contributionRules,
                actionLabel = if (isAdmin) "+ Rule" else null,
                onAction = if (isAdmin) onAddRule else null
            )
        }
        if (snapshot.contributionRules.isEmpty()) {
            item { EmptyStateCard(Icons.Default.Rule, strings.noData) }
        } else {
            items(snapshot.contributionRules, key = { it.id }) { r ->
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(r.name, fontWeight = FontWeight.SemiBold)
                            Text(r.ruleType.replace("_", " "), style = MaterialTheme.typography.labelSmall)
                        }
                        Text(Formatters.formatBdt(r.amount), fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = if (isTreasurerOrAdmin) "Member Financial Statements" else "My Financial Statement",
                actionLabel = if (isTreasurerOrAdmin) "Pay" else null,
                onAction = if (isTreasurerOrAdmin) onRecordPayment else null
            )
        }
        items(visibleDues, key = { it.id }) { due ->
            val m = snapshot.members.firstOrNull { it.id == due.memberId }
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${m?.fullName ?: "Member"} (${m?.memberCode ?: ""})", fontWeight = FontWeight.Bold)
                        Text(
                            "Due: ${Formatters.formatBdt(due.outstandingBalance)}",
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = if (due.outstandingBalance > 0) StatusError else AthleticGreen
                        )
                    }
                    Text(
                        "Payable: ${Formatters.formatBdt(due.totalPayable)} | Paid: ${Formatters.formatBdt(due.totalPaid)} | Adv: ${Formatters.formatBdt(due.advanceBalance)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpenseApprovalPane(
    snapshot: TeamWorkspaceSnapshot,
    strings: UiStrings,
    isAdmin: Boolean,
    isTreasurerOrAdmin: Boolean,
    onSubmitExpense: () -> Unit,
    onReview: (String, String, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isTreasurerOrAdmin) {
            item {
                Button(
                    onClick = onSubmitExpense,
                    modifier = Modifier.fillMaxWidth().testTag("submit_expense_req_btn")
                ) {
                    Icon(Icons.Default.AddCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.submitExpenseRequest)
                }
            }
        }
        if (snapshot.expenseRequests.isEmpty()) {
            item { EmptyStateCard(Icons.Default.RequestQuote, strings.noData) }
        } else {
            items(snapshot.expenseRequests, key = { it.id }) { req ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(req.category, fontWeight = FontWeight.Bold)
                            StatusPill(
                                req.status,
                                when (req.status) {
                                    "APPROVED" -> AthleticGreen
                                    "REJECTED" -> StatusError
                                    else -> StatusWarning
                                }
                            )
                        }
                        Text(Formatters.formatBdt(req.requestedAmount), fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold)
                        Text(req.description, style = MaterialTheme.typography.bodySmall)
                        if (isAdmin && (req.status == "PENDING" || req.status == "MODIFICATION_REQUESTED")) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onReview(req.id, "APPROVED", "Approved by Super Admin") },
                                    modifier = Modifier.weight(1f)
                                ) { Text(strings.approve) }
                                OutlinedButton(
                                    onClick = { onReview(req.id, "REJECTED", "Rejected") },
                                    modifier = Modifier.weight(1f)
                                ) { Text(strings.reject) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetsAndReportsPane(
    snapshot: TeamWorkspaceSnapshot,
    strings: UiStrings,
    isAdmin: Boolean,
    onAddBudgetGoal: () -> Unit,
    onExportPdf: () -> Unit,
    onExportExcel: () -> Unit
) {
    val approvedExpenses = snapshot.transactions.filter { it.type == "EXPENSE" && it.approvalStatus == "APPROVED" }.sumOf { it.amount }
    val approvedIncome = snapshot.transactions.filter { it.type == "INCOME" && it.approvalStatus == "APPROVED" }.sumOf { it.amount }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onExportPdf, modifier = Modifier.weight(1f).testTag("export_pdf_btn")) {
                    Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.exportMonthlyPdf, maxLines = 1)
                }
                FilledTonalButton(onClick = onExportExcel, modifier = Modifier.weight(1f).testTag("export_excel_btn")) {
                    Icon(Icons.Default.TableChart, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.exportMonthlyExcel, maxLines = 1)
                }
            }
        }

        item {
            SectionHeader(
                title = strings.budgetsAndGoals,
                actionLabel = if (isAdmin) "+ Budget / Goal" else null,
                onAction = if (isAdmin) onAddBudgetGoal else null
            )
        }

        items(snapshot.budgets, key = { it.id }) { b ->
            val ratio = if (b.budgetAmount > 0) (approvedExpenses / b.budgetAmount).toFloat() else 0f
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${b.title} (${b.periodKey})", fontWeight = FontWeight.Bold)
                        Text("${(ratio * 100).toInt()}%", fontFamily = MonoFontFamily)
                    }
                    LinearProgressIndicator(
                        progress = { ratio.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = if (ratio > 1f) StatusError else AthleticGreen
                    )
                    Text("Budget: ${Formatters.formatBdt(b.budgetAmount)} | Spent: ${Formatters.formatBdt(approvedExpenses)}")
                }
            }
        }

        items(snapshot.fundGoals, key = { it.id }) { g ->
            val progress = if (g.targetAmount > 0) (approvedIncome / g.targetAmount).toFloat().coerceIn(0f, 1f) else 0f
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(g.title, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp))
                    Text("Goal: ${Formatters.formatBdt(g.targetAmount)} • Collected: ${Formatters.formatBdt(approvedIncome)}")
                }
            }
        }

        if (snapshot.monthlyReports.isNotEmpty()) {
            item { SectionHeader(title = "Archived Monthly Reports") }
            items(snapshot.monthlyReports, key = { it.id }) { r ->
                ListItem(
                    headlineContent = { Text("Period: ${r.reportPeriod}", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Closing: ${Formatters.formatBdt(r.closingBalance)} | Dues: ${Formatters.formatBdt(r.outstandingDues)}") },
                    trailingContent = { Text("Saved", color = AthleticGreen) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordPaymentDialog(
    members: List<TeamMember>,
    accounts: List<FundAccount>,
    onDismiss: () -> Unit,
    onSave: (String, Double, String, String, Boolean, String) -> Unit
) {
    var memberId by remember { mutableStateOf(members.firstOrNull()?.id.orEmpty()) }
    var accountId by remember { mutableStateOf(accounts.firstOrNull()?.id.orEmpty()) }
    var amount by remember { mutableStateOf("500") }
    var method by remember { mutableStateOf("CASH") }
    var isAdvance by remember { mutableStateOf(false) }
    var desc by remember { mutableStateOf("Monthly Contribution") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Member Payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                var mExp by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = mExp, onExpandedChange = { mExp = it }) {
                    OutlinedTextField(
                        value = members.firstOrNull { it.id == memberId }?.fullName ?: "Select Member",
                        onValueChange = {}, readOnly = true, label = { Text("Member") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(mExp) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = mExp, onDismissRequest = { mExp = false }) {
                        members.forEach { m -> DropdownMenuItem(text = { Text(m.fullName) }, onClick = { memberId = m.id; mExp = false }) }
                    }
                }
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount (৳ BDT)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("CASH", "BKASH", "NAGAD", "BANK").forEach { pm ->
                        FilterChip(selected = method == pm, onClick = { method = pm }, label = { Text(pm, style = MaterialTheme.typography.labelSmall) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isAdvance, onCheckedChange = { isAdvance = it })
                    Text("Advance Payment")
                }
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(memberId, amount.toDoubleOrNull() ?: 0.0, accountId, method, isAdvance, desc) },
                enabled = memberId.isNotBlank() && accountId.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0
            ) { Text("Record") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RecordIncomeDialog(accounts: List<FundAccount>, onDismiss: () -> Unit, onSave: (String, Double, String, String, String) -> Unit) {
    var category by remember { mutableStateOf("Donations") }
    var amount by remember { mutableStateOf("") }
    var accountId by remember { mutableStateOf(accounts.firstOrNull()?.id.orEmpty()) }
    var desc by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Income") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Donations", "Sponsorship", "Other Income").forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c, style = MaterialTheme.typography.labelSmall) })
                    }
                }
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount (৳)") }, singleLine = true)
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") })
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(category, amount.toDoubleOrNull() ?: 0.0, accountId, "CASH", desc) },
                enabled = accountId.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0 && desc.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SubmitExpenseRequestDialog(
    accounts: List<FundAccount>,
    games: List<MatchGame>,
    onDismiss: () -> Unit,
    onSubmit: (Double, String, String, String?, String?) -> Unit
) {
    var category by remember { mutableStateOf("Ground Rental") }
    var amount by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    val accountId = accounts.firstOrNull()?.id
    val gameId = games.firstOrNull()?.id

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Submit Expense Request") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Ground Rental", "Referee Fees", "Food & Refreshments", "Jerseys").forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.take(8), style = MaterialTheme.typography.labelSmall) })
                    }
                }
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount (৳ BDT)") }, singleLine = true)
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Expense Details") })
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(amount.toDoubleOrNull() ?: 0.0, category, desc, gameId, accountId) },
                enabled = (amount.toDoubleOrNull() ?: 0.0) > 0 && desc.isNotBlank()
            ) { Text("Submit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun FundTransferDialog(accounts: List<FundAccount>, onDismiss: () -> Unit, onTransfer: (String, String, Double, String) -> Unit) {
    var fromId by remember { mutableStateOf(accounts.getOrNull(0)?.id.orEmpty()) }
    var toId by remember { mutableStateOf(accounts.getOrNull(1)?.id.orEmpty()) }
    var amount by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Balanced Fund Transfer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("From: ${accounts.firstOrNull { it.id == fromId }?.name ?: "-"} -> To: ${accounts.firstOrNull { it.id == toId }?.name ?: "-"}")
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount (৳)") }, singleLine = true)
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = { onTransfer(fromId, toId, amount.toDoubleOrNull() ?: 0.0, notes) },
                enabled = fromId.isNotBlank() && toId.isNotBlank() && fromId != toId && (amount.toDoubleOrNull() ?: 0.0) > 0
            ) { Text("Transfer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ContributionRuleDialog(members: List<TeamMember>, onDismiss: () -> Unit, onSave: (String, String, Double, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var ruleType by remember { mutableStateOf("ATTENDANCE_BASED") }
    var amount by remember { mutableStateOf("50") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure Contribution Rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Rule Name") }, singleLine = true)
                Column {
                    listOf("ATTENDANCE_BASED", "FIXED_PER_MATCH", "MONTHLY_FIXED", "EVEN_WHEN_ABSENT", "SPECIAL", "MEMBER_DISCOUNT").forEach { rt ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = ruleType == rt, onClick = { ruleType = rt })
                            Text(rt.replace("_", " "), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount (৳ BDT)") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, ruleType, amount.toDoubleOrNull() ?: 0.0, null) },
                enabled = name.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) >= 0
            ) { Text("Save Rule") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun BudgetOrGoalDialog(onDismiss: () -> Unit, onSave: (Boolean, String, Double, String) -> Unit) {
    var isBudget by remember { mutableStateOf(true) }
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var period by remember { mutableStateOf(Formatters.todayDhaka().toString().substring(0, 7)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isBudget) "Monthly Budget" else "Fundraising Goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = isBudget, onClick = { isBudget = true }, label = { Text("Budget") })
                    FilterChip(selected = !isBudget, onClick = { isBudget = false }, label = { Text("Fund Goal") })
                }
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Target Amount (৳)") }, singleLine = true)
                OutlinedTextField(value = period, onValueChange = { period = it }, label = { Text(if (isBudget) "Month (YYYY-MM)" else "Deadline (YYYY-MM-DD)") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(isBudget, title, amount.toDoubleOrNull() ?: 0.0, period) },
                enabled = title.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
