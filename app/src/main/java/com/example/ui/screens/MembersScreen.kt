package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
fun MembersScreen(
    snapshot: TeamWorkspaceSnapshot,
    strings: UiStrings,
    viewModel: ClubViewModel
) {
    val isAdmin = snapshot.currentMember.userRole == UserRole.SUPER_ADMIN
    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<String>("ALL") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showBirthdaysDialog by remember { mutableStateOf(false) }
    var selectedMember by remember { mutableStateOf<TeamMember?>(null) }

    val pendingMembers = snapshot.members.filter { it.regStatus == RegistrationStatus.PENDING }
    val filteredMembers = snapshot.members.filter { m ->
        val matchesQuery = search.isBlank() ||
            m.fullName.contains(search, true) ||
            m.memberCode.contains(search, true) ||
            m.mobileNumber.contains(search, true)
        val matchesStatus = statusFilter == "ALL" || m.status == statusFilter
        matchesQuery && matchesStatus && m.regStatus != RegistrationStatus.PENDING
    }

    Scaffold(
        floatingActionButton = {
            if (isAdmin) {
                ExtendedFloatingActionButton(
                    onClick = { showAddDialog = true },
                    icon = { Icon(Icons.Default.PersonAdd, null) },
                    text = { Text("Add Member") },
                    modifier = Modifier.testTag("add_member_fab")
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.navMembers, style = MaterialTheme.typography.headlineSmall)
                    OutlinedButton(
                        onClick = { showBirthdaysDialog = true },
                        modifier = Modifier.testTag("birthday_calendar_btn")
                    ) {
                        Icon(Icons.Default.Cake, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.birthdayCalendar)
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text(strings.searchHint) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("members_search_input")
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("ALL", "ACTIVE", "INACTIVE", "SUSPENDED", "FORMER_MEMBER").forEach { st ->
                        FilterChip(
                            selected = statusFilter == st,
                            onClick = { statusFilter = st },
                            label = { Text(st.replace("_", " "), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            if (isAdmin && pendingMembers.isNotEmpty()) {
                item { SectionHeader(title = "${strings.pendingApprovals} (${pendingMembers.size})") }
                items(pendingMembers, key = { it.id }) { pm ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${pm.fullName} (${pm.email})", fontWeight = FontWeight.Bold)
                            Text("Mobile: ${pm.mobileNumber}", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        viewModel.updateMemberRoleStatus(pm.id, UserRole.MEMBER, MemberStatus.ACTIVE, RegistrationStatus.APPROVED)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text(strings.approve) }
                                OutlinedButton(
                                    onClick = {
                                        viewModel.updateMemberRoleStatus(pm.id, UserRole.MEMBER, MemberStatus.INACTIVE, RegistrationStatus.REJECTED)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text(strings.reject) }
                            }
                        }
                    }
                }
            }

            if (filteredMembers.isEmpty()) {
                item { EmptyStateCard(Icons.Default.GroupOff, strings.noData) }
            } else {
                items(filteredMembers, key = { it.id }) { member ->
                    val due = snapshot.memberDues.firstOrNull { it.memberId == member.id }
                    val canSeeFinance = snapshot.currentMember.userRole != UserRole.MEMBER || member.id == snapshot.currentMember.id
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMember = member }
                            .testTag("member_card_${member.memberCode}")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(member.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    RoleBadge(member.userRole)
                                }
                                Text(
                                    "${member.memberCode} • ${member.preferredPosition} • Jersey ${member.jerseySize}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                StatusPill(
                                    member.status,
                                    when (member.memberStatus) {
                                        MemberStatus.ACTIVE -> AthleticGreen
                                        MemberStatus.INACTIVE -> StatusWarning
                                        MemberStatus.SUSPENDED -> StatusError
                                        MemberStatus.FORMER_MEMBER -> NavyLight
                                    }
                                )
                            }
                            if (canSeeFinance && due != null) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Due", style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        Formatters.formatBdt(due.outstandingBalance),
                                        fontFamily = MonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = if (due.outstandingBalance > 0) StatusError else AthleticGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddMemberDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, email, mobile, wa, dob, size, pos, role ->
                viewModel.addMember(name, email, mobile, wa, dob, size, pos, role)
                showAddDialog = false
            }
        )
    }

    if (showBirthdaysDialog) {
        AlertDialog(
            onDismissRequest = { showBirthdaysDialog = false },
            title = { Text(strings.birthdayCalendar) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val bdays = snapshot.members.filter { !it.dateOfBirth.isNullOrBlank() }
                    if (bdays.isEmpty()) {
                        Text(strings.noData)
                    } else {
                        bdays.forEach { m ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(m.fullName, fontWeight = FontWeight.Medium)
                                Text(m.dateOfBirth ?: "", fontFamily = MonoFontFamily)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBirthdaysDialog = false }) { Text("OK") } }
        )
    }

    selectedMember?.let { member ->
        MemberDetailDialog(
            member = member,
            snapshot = snapshot,
            isAdmin = isAdmin,
            onDismiss = { selectedMember = null },
            onUpdateRoleStatus = { role, status ->
                viewModel.updateMemberRoleStatus(member.id, role, status, RegistrationStatus.APPROVED)
                selectedMember = null
            }
        )
    }
}

@Composable
private fun AddMemberDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String?, String, String, UserRole) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var size by remember { mutableStateOf("L") }
    var position by remember { mutableStateOf("Midfielder") }
    var role by remember { mutableStateOf(UserRole.MEMBER) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Journalist Member") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, singleLine = true)
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true)
                OutlinedTextField(value = mobile, onValueChange = { mobile = it }, label = { Text("Mobile / WhatsApp") }, singleLine = true)
                OutlinedTextField(value = dob, onValueChange = { dob = it }, label = { Text("Birthday (YYYY-MM-DD)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = size, onValueChange = { size = it }, label = { Text("Jersey Size") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = position, onValueChange = { position = it }, label = { Text("Position") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    UserRole.entries.forEach { r ->
                        FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r.name, style = MaterialTheme.typography.labelSmall) })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, email, mobile, mobile, dob.ifBlank { null }, size, position, role) },
                enabled = name.isNotBlank() && email.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun MemberDetailDialog(
    member: TeamMember,
    snapshot: TeamWorkspaceSnapshot,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onUpdateRoleStatus: (UserRole, MemberStatus) -> Unit
) {
    val totalMatches = snapshot.matches.count { it.status == "COMPLETED" || it.status == "UPCOMING" }
    val presentCount = snapshot.attendance.count { it.memberId == member.id && (it.status == "PRESENT" || it.status == "LATE") }
    val absentCount = snapshot.attendance.count { it.memberId == member.id && it.status == "ABSENT" }
    val attPct = if (presentCount + absentCount > 0) (presentCount * 100) / (presentCount + absentCount) else 0
    val due = snapshot.memberDues.firstOrNull { it.memberId == member.id }
    val canSeeFinance = snapshot.currentMember.userRole != UserRole.MEMBER || member.id == snapshot.currentMember.id

    var editRole by remember { mutableStateOf(member.userRole) }
    var editStatus by remember { mutableStateOf(member.memberStatus) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${member.fullName} (${member.memberCode})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Email: ${member.email}")
                Text("Mobile: ${member.mobileNumber} | WhatsApp: ${member.whatsappNumber}")
                Text("Position: ${member.preferredPosition} | Jersey: ${member.jerseySize}")
                HorizontalDivider()
                Text("Matches: $totalMatches | Present: $presentCount | Absent: $absentCount ($attPct%)", fontWeight = FontWeight.SemiBold)
                if (canSeeFinance && due != null) {
                    Text("Payable: ${Formatters.formatBdt(due.totalPayable)} | Paid: ${Formatters.formatBdt(due.totalPaid)}")
                    Text("Outstanding: ${Formatters.formatBdt(due.outstandingBalance)} | Advance: ${Formatters.formatBdt(due.advanceBalance)}", fontWeight = FontWeight.Bold)
                }
                if (isAdmin) {
                    HorizontalDivider()
                    Text("Assign Role", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        UserRole.entries.forEach { r ->
                            FilterChip(selected = editRole == r, onClick = { editRole = r }, label = { Text(r.name, style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                    Text("Member Status", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MemberStatus.entries.forEach { st ->
                            FilterChip(selected = editStatus == st, onClick = { editStatus = st }, label = { Text(st.name.take(6), style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isAdmin) {
                Button(onClick = { onUpdateRoleStatus(editRole, editStatus) }) { Text("Update") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
