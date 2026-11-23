package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.core.i18n.Formatters
import com.example.core.i18n.UiStrings
import com.example.data.repository.TeamWorkspaceSnapshot
import com.example.domain.model.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.ClubViewModel
import com.example.utils.ExternalIntents
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun MatchesScreen(
    snapshot: TeamWorkspaceSnapshot,
    strings: UiStrings,
    viewModel: ClubViewModel
) {
    val context = LocalContext.current
    val isAdmin = snapshot.currentMember.userRole == UserRole.SUPER_ADMIN
    val isTreasurerOrAdmin = snapshot.currentMember.userRole != UserRole.MEMBER
    var subTab by remember { mutableIntStateOf(0) }
    var showCreateMatch by remember { mutableStateOf(false) }
    var resultTargetGame by remember { mutableStateOf<MatchGame?>(null) }
    var attendanceTargetGame by remember { mutableStateOf<MatchGame?>(null) }
    var showLeaveDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            if (subTab == 0 && isAdmin) {
                ExtendedFloatingActionButton(
                    onClick = { showCreateMatch = true },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text(strings.createMatch) },
                    modifier = Modifier.testTag("create_match_fab")
                )
            } else if (subTab == 3) {
                ExtendedFloatingActionButton(
                    onClick = { showLeaveDialog = true },
                    icon = { Icon(Icons.Default.EventBusy, null) },
                    text = { Text(strings.applyLeave) },
                    modifier = Modifier.testTag("apply_leave_fab")
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = subTab, edgePadding = 12.dp) {
                Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text(strings.navMatches) })
                Tab(selected = subTab == 1, onClick = { subTab = 1 }, text = { Text(strings.availabilityCalendar) })
                Tab(selected = subTab == 2, onClick = { subTab = 2 }, text = { Text(strings.attendance) })
                Tab(selected = subTab == 3, onClick = { subTab = 3 }, text = { Text(strings.leaveApplications) })
            }

            when (subTab) {
                0 -> MatchListPane(
                    snapshot = snapshot,
                    strings = strings,
                    isAdmin = isAdmin,
                    isTreasurerOrAdmin = isTreasurerOrAdmin,
                    onOpenMaps = { m -> ExternalIntents.openGoogleMaps(context, m.venueName, m.mapsUrl) },
                    onRecordResult = { resultTargetGame = it },
                    onRecordAttendance = { attendanceTargetGame = it },
                    onUpdateStatus = { id, status -> viewModel.updateMatchStatus(id, status) }
                )
                1 -> AvailabilityCalendarPane(snapshot = snapshot, strings = strings, isAdmin = isAdmin, viewModel = viewModel)
                2 -> AttendanceHistoryPane(snapshot = snapshot, strings = strings)
                3 -> LeaveApplicationsPane(snapshot = snapshot, strings = strings, isAdmin = isAdmin, viewModel = viewModel)
            }
        }
    }

    if (showCreateMatch) {
        CreateMatchDialog(
            onDismiss = { showCreateMatch = false },
            onConfirm = { title, opp, date, time, venue, maps, desc, budget ->
                viewModel.createMatch(title, opp, date, time, venue, maps, desc, budget)
                showCreateMatch = false
            }
        )
    }

    resultTargetGame?.let { game ->
        MatchResultDialog(
            game = game,
            members = snapshot.members.filter { it.status == "ACTIVE" },
            onDismiss = { resultTargetGame = null },
            onSave = { tScore, oScore, motmId, notes ->
                viewModel.saveMatchResult(game.id, game.opponentName, game.matchDate, tScore, oScore, motmId, notes)
                resultTargetGame = null
            }
        )
    }

    attendanceTargetGame?.let { game ->
        AttendanceSheetDialog(
            game = game,
            snapshot = snapshot,
            onDismiss = { attendanceTargetGame = null },
            onMark = { memberId, status -> viewModel.recordAttendance(game.id, memberId, status, null) }
        )
    }

    if (showLeaveDialog) {
        SubmitLeaveDialog(
            onDismiss = { showLeaveDialog = false },
            onSubmit = { date, reason, note ->
                viewModel.submitLeaveRequest(date, reason, note)
                showLeaveDialog = false
            }
        )
    }
}

@Composable
private fun MatchListPane(
    snapshot: TeamWorkspaceSnapshot,
    strings: UiStrings,
    isAdmin: Boolean,
    isTreasurerOrAdmin: Boolean,
    onOpenMaps: (MatchGame) -> Unit,
    onRecordResult: (MatchGame) -> Unit,
    onRecordAttendance: (MatchGame) -> Unit,
    onUpdateStatus: (String, MatchStatus) -> Unit
) {
    val activeMembers = snapshot.members.filter { it.status == "ACTIVE" && it.regStatus == RegistrationStatus.APPROVED }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(strings.matchScheduleNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }

        if (snapshot.matches.isEmpty()) {
            item { EmptyStateCard(Icons.Default.SportsSoccer, strings.noData) }
        } else {
            items(snapshot.matches, key = { it.id }) { match ->
                val availsForDate = snapshot.availability.filter { it.availDate == match.matchDate }
                val availCount = availsForDate.count { it.availabilityStatus == AvailabilityStatus.AVAILABLE }
                val unavailCount = availsForDate.count { it.availabilityStatus == AvailabilityStatus.UNAVAILABLE }
                val notRespCount = (activeMembers.size - availCount - unavailCount).coerceAtLeast(0)
                val result = snapshot.matchResults.firstOrNull { it.gameId == match.id }
                val motmName = result?.manOfTheMatchMemberId?.let { id -> snapshot.members.firstOrNull { it.id == id }?.fullName }

                ElevatedCard(modifier = Modifier.fillMaxWidth().testTag("match_card_${match.id}")) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(match.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            StatusPill(
                                match.status,
                                when (match.matchStatus) {
                                    MatchStatus.UPCOMING -> AthleticGreen
                                    MatchStatus.COMPLETED -> NavyPrimary
                                    MatchStatus.POSTPONED -> StatusWarning
                                    MatchStatus.CANCELLED -> StatusError
                                }
                            )
                        }
                        Text("vs ${match.opponentName} • ${match.matchDate} at ${match.matchTime.take(5)}")
                        Text("Venue: ${match.venueName} • Budget: ${Formatters.formatBdt(match.expectedBudget)}", style = MaterialTheme.typography.bodySmall)

                        // Availability Summary Breakdown for Match Date
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusPill("${strings.available}: $availCount", AthleticGreen)
                            StatusPill("${strings.unavailable}: $unavailCount", StatusError)
                            StatusPill("${strings.notResponded}: $notRespCount", StatusWarning)
                        }

                        if (result != null) {
                            Surface(color = GoldContainer, shape = RoundedCornerShape(10.dp)) {
                                Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                    Text(
                                        "Result: ${result.teamScore} - ${result.opponentScore} (${result.outcome})",
                                        fontWeight = FontWeight.Bold,
                                        color = NavyDark
                                    )
                                    if (!motmName.isNullOrBlank()) {
                                        Text("${strings.manOfTheMatch}: $motmName", style = MaterialTheme.typography.bodySmall, color = NavyDark)
                                    }
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { onOpenMaps(match) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Map, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Maps", maxLines = 1)
                            }
                            if (isTreasurerOrAdmin) {
                                FilledTonalButton(
                                    onClick = { onRecordAttendance(match) },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Attendance", maxLines = 1) }
                            }
                            if (isAdmin) {
                                Button(
                                    onClick = { onRecordResult(match) },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Result", maxLines = 1) }
                            }
                        }
                        if (isAdmin && match.matchStatus == MatchStatus.UPCOMING) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { onUpdateStatus(match.id, MatchStatus.POSTPONED) }) { Text("Postpone") }
                                TextButton(onClick = { onUpdateStatus(match.id, MatchStatus.CANCELLED) }) {
                                    Text("Cancel Match", color = StatusError)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AvailabilityCalendarPane(
    snapshot: TeamWorkspaceSnapshot,
    strings: UiStrings,
    isAdmin: Boolean,
    viewModel: ClubViewModel
) {
    var currentMonth by remember { mutableStateOf(YearMonth.from(Formatters.todayDhaka())) }
    var selectedDates by remember { mutableStateOf(setOf(Formatters.todayDhaka().toString())) }
    var targetMemberId by remember { mutableStateOf(snapshot.currentMember.id) }
    var remarks by remember { mutableStateOf("") }

    val targetMember = snapshot.members.firstOrNull { it.id == targetMemberId } ?: snapshot.currentMember
    val memberAvailMap = snapshot.availability
        .filter { it.memberId == targetMember.id }
        .associateBy { it.availDate }

    val daysInMonth = currentMonth.lengthOfMonth()
    val firstDayOffset = (currentMonth.atDay(1).dayOfWeek.value % 7)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isAdmin) {
            item {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = "${targetMember.fullName} (${targetMember.memberCode})",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("${strings.overrideAvailability} — Select Member") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        snapshot.members.filter { it.status == "ACTIVE" }.forEach { m ->
                            DropdownMenuItem(
                                text = { Text("${m.fullName} (${m.memberCode})") },
                                onClick = { targetMemberId = m.id; expanded = false }
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month")
                }
                Text("${currentMonth.month.name} ${currentMonth.year}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next month")
                }
            }
        }

        item {
            val allMonthDates = (1..daysInMonth).map { currentMonth.atDay(it).toString() }.toSet()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { selectedDates = allMonthDates },
                    modifier = Modifier.weight(1f).testTag("select_entire_month_btn")
                ) { Text(strings.setEntireMonth, maxLines = 1) }
                OutlinedButton(
                    onClick = { selectedDates = emptySet() },
                    modifier = Modifier.weight(1f)
                ) { Text("Clear Selection", maxLines = 1) }
            }
        }

        item {
            val cells = List(firstDayOffset) { null } + (1..daysInMonth).map { currentMonth.atDay(it) }
            val rows = cells.chunked(7)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { d ->
                        Text(d, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
                    }
                }
                rows.forEach { week ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (i in 0 until 7) {
                            val dateObj: LocalDate? = week.getOrNull(i)
                            if (dateObj == null) {
                                Spacer(Modifier.weight(1f).height(44.dp))
                            } else {
                                val dateStr = dateObj.toString()
                                val isSel = selectedDates.contains(dateStr)
                                val st = memberAvailMap[dateStr]?.availabilityStatus ?: AvailabilityStatus.NOT_RESPONDED
                                val bg = when (st) {
                                    AvailabilityStatus.AVAILABLE -> AthleticGreen.copy(alpha = 0.22f)
                                    AvailabilityStatus.UNAVAILABLE -> StatusError.copy(alpha = 0.20f)
                                    AvailabilityStatus.NOT_RESPONDED -> MaterialTheme.colorScheme.surfaceVariant
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(bg)
                                        .border(
                                            width = if (isSel) 2.dp else 0.dp,
                                            color = if (isSel) NavyPrimary else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            selectedDates = if (isSel) selectedDates - dateStr else selectedDates + dateStr
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        dateObj.dayOfMonth.toString(),
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = remarks,
                onValueChange = { remarks = it },
                label = { Text("Remarks (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        item {
            val isOverride = isAdmin && targetMember.id != snapshot.currentMember.id
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        if (selectedDates.isNotEmpty()) {
                            viewModel.submitAvailability(targetMember.id, selectedDates.toList(), AvailabilityStatus.AVAILABLE, remarks.ifBlank { null }, isOverride)
                        }
                    },
                    enabled = selectedDates.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = AthleticGreen),
                    modifier = Modifier.weight(1f).testTag("mark_available_btn")
                ) { Text(strings.available) }

                Button(
                    onClick = {
                        if (selectedDates.isNotEmpty()) {
                            viewModel.submitAvailability(targetMember.id, selectedDates.toList(), AvailabilityStatus.UNAVAILABLE, remarks.ifBlank { null }, isOverride)
                        }
                    },
                    enabled = selectedDates.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusError),
                    modifier = Modifier.weight(1f).testTag("mark_unavailable_btn")
                ) { Text(strings.unavailable) }
            }
        }
    }
}

@Composable
private fun AttendanceHistoryPane(snapshot: TeamWorkspaceSnapshot, strings: UiStrings) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (snapshot.attendance.isEmpty()) {
            item { EmptyStateCard(Icons.Default.FactCheck, strings.noData) }
        } else {
            items(snapshot.attendance, key = { it.id }) { att ->
                val member = snapshot.members.firstOrNull { it.id == att.memberId }
                val game = snapshot.matches.firstOrNull { it.id == att.gameId }
                ListItem(
                    headlineContent = { Text(member?.fullName ?: "Member") },
                    supportingContent = { Text("${game?.title ?: "Match"} • ${game?.matchDate ?: ""}") },
                    trailingContent = {
                        StatusPill(
                            att.status,
                            when (att.attendanceStatus) {
                                AttendanceStatus.PRESENT -> AthleticGreen
                                AttendanceStatus.LATE -> StatusInfo
                                AttendanceStatus.EXCUSED -> StatusWarning
                                AttendanceStatus.ABSENT -> StatusError
                            }
                        )
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun LeaveApplicationsPane(
    snapshot: TeamWorkspaceSnapshot,
    strings: UiStrings,
    isAdmin: Boolean,
    viewModel: ClubViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (snapshot.leaveRequests.isEmpty()) {
            item { EmptyStateCard(Icons.Default.EventNote, strings.noData) }
        } else {
            items(snapshot.leaveRequests, key = { it.id }) { lr ->
                val member = snapshot.members.firstOrNull { it.id == lr.memberId }
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${member?.fullName ?: "Member"} • ${lr.leaveDate}", fontWeight = FontWeight.Bold)
                            StatusPill(
                                lr.status,
                                when (lr.status) {
                                    "APPROVED" -> AthleticGreen
                                    "REJECTED" -> StatusError
                                    else -> StatusWarning
                                }
                            )
                        }
                        Text(lr.reason)
                        if (!lr.additionalNote.isNullOrBlank()) {
                            Text(lr.additionalNote, style = MaterialTheme.typography.bodySmall)
                        }
                        if (isAdmin && lr.status == "PENDING") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.reviewLeaveRequest(lr.id, "APPROVED", "Approved") },
                                    modifier = Modifier.weight(1f)
                                ) { Text(strings.approve) }
                                OutlinedButton(
                                    onClick = { viewModel.reviewLeaveRequest(lr.id, "REJECTED", "Rejected") },
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
private fun CreateMatchDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String, String?, String?, Double) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var opponent by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(Formatters.todayDhaka().plusDays(3).toString()) }
    var time by remember { mutableStateOf("16:30:00") }
    var venue by remember { mutableStateOf("") }
    var mapsUrl by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf("2500") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Publish New Match") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Match Title") }, singleLine = true)
                OutlinedTextField(value = opponent, onValueChange = { opponent = it }, label = { Text("Opponent Name") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text("Time (HH:MM:SS)") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(value = venue, onValueChange = { venue = it }, label = { Text("Venue") }, singleLine = true)
                OutlinedTextField(value = mapsUrl, onValueChange = { mapsUrl = it }, label = { Text("Google Maps URL (Optional)") }, singleLine = true)
                OutlinedTextField(value = budget, onValueChange = { budget = it }, label = { Text("Expected Budget (৳)") }, singleLine = true)
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title, opponent, date, time, venue, mapsUrl.ifBlank { null }, desc.ifBlank { null }, budget.toDoubleOrNull() ?: 0.0) },
                enabled = title.isNotBlank() && opponent.isNotBlank() && venue.isNotBlank()
            ) { Text("Publish") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchResultDialog(
    game: MatchGame,
    members: List<TeamMember>,
    onDismiss: () -> Unit,
    onSave: (Int, Int, String?, String?) -> Unit
) {
    var teamScore by remember { mutableStateOf("2") }
    var oppScore by remember { mutableStateOf("1") }
    var motmId by remember { mutableStateOf(members.firstOrNull()?.id) }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Match Result: ${game.title}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = teamScore, onValueChange = { teamScore = it }, label = { Text("JSC Score") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = oppScore, onValueChange = { oppScore = it }, label = { Text("${game.opponentName}") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                var expanded by remember { mutableStateOf(false) }
                val motmName = members.firstOrNull { it.id == motmId }?.fullName ?: "Select Player"
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = motmName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Man of the Match") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        members.forEach { m ->
                            DropdownMenuItem(text = { Text(m.fullName) }, onClick = { motmId = m.id; expanded = false })
                        }
                    }
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Match Notes") })
            }
        },
        confirmButton = {
            Button(onClick = { onSave(teamScore.toIntOrNull() ?: 0, oppScore.toIntOrNull() ?: 0, motmId, notes.ifBlank { null }) }) { Text("Save Result") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AttendanceSheetDialog(
    game: MatchGame,
    snapshot: TeamWorkspaceSnapshot,
    onDismiss: () -> Unit,
    onMark: (String, AttendanceStatus) -> Unit
) {
    val activeMembers = snapshot.members.filter { it.status == "ACTIVE" && it.regStatus == RegistrationStatus.APPROVED }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Final Attendance: ${game.title}") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(activeMembers, key = { it.id }) { m ->
                    val currentAtt = snapshot.attendance.firstOrNull { it.gameId == game.id && it.memberId == m.id }?.attendanceStatus
                    Column {
                        Text(m.fullName, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            AttendanceStatus.entries.forEach { st ->
                                FilterChip(
                                    selected = currentAtt == st,
                                    onClick = { onMark(m.id, st) },
                                    label = { Text(st.name.take(3), style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun SubmitLeaveDialog(onDismiss: () -> Unit, onSubmit: (String, String, String?) -> Unit) {
    var date by remember { mutableStateOf(Formatters.todayDhaka().toString()) }
    var reason by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Leave Application") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Leave Date (YYYY-MM-DD)") }, singleLine = true)
                OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason") })
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Additional Note (Optional)") })
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(date, reason, note.ifBlank { null }) }, enabled = reason.isNotBlank()) { Text("Submit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
