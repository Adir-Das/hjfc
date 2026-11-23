package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.Formatters
import com.example.core.i18n.UiStrings
import com.example.data.repository.TeamWorkspaceSnapshot
import com.example.domain.model.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.ClubUiState
import com.example.ui.viewmodel.ClubViewModel
import com.example.utils.ExternalIntents

@Composable
fun AnnouncementsAndPollsPane(
    snapshot: TeamWorkspaceSnapshot,
    strings: UiStrings,
    isAdmin: Boolean,
    viewModel: ClubViewModel
) {
    var showAnnouncementDialog by remember { mutableStateOf(false) }
    var showPollDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isAdmin) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { showAnnouncementDialog = true }, modifier = Modifier.weight(1f)) {
                        Text("+ Announcement", maxLines = 1)
                    }
                    FilledTonalButton(onClick = { showPollDialog = true }, modifier = Modifier.weight(1f)) {
                        Text("+ General Poll", maxLines = 1)
                    }
                }
            }
        }

        item { SectionHeader(title = strings.announcements) }
        if (snapshot.announcements.isEmpty()) {
            item { EmptyStateCard(Icons.Default.Campaign, strings.noData) }
        } else {
            items(snapshot.announcements, key = { it.id }) { ann ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(ann.title, fontWeight = FontWeight.Bold)
                            if (ann.isPinned) StatusPill("Pinned", GoldAccent)
                        }
                        Text(ann.content, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        item { SectionHeader(title = strings.teamPolls) }
        if (snapshot.polls.isEmpty()) {
            item { EmptyStateCard(Icons.Default.Poll, strings.noData) }
        } else {
            items(snapshot.polls, key = { it.id }) { poll ->
                val opts = snapshot.pollOptions.filter { it.pollId == poll.id }
                val votes = snapshot.pollVotes.filter { it.pollId == poll.id }
                val myVote = votes.firstOrNull { it.memberId == snapshot.currentMember.id }
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(poll.title, fontWeight = FontWeight.Bold)
                            StatusPill(poll.pollCategory, NavyPrimary)
                        }
                        opts.forEach { opt ->
                            val count = votes.count { it.optionId == opt.id }
                            val chosen = myVote?.optionId == opt.id
                            OutlinedButton(
                                onClick = { if (myVote == null && !poll.isClosed) viewModel.castVote(poll.id, opt.id) },
                                enabled = myVote == null && !poll.isClosed,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${if (chosen) "✓ " else ""}${opt.optionText}")
                                    Text("$count vote(s)", fontFamily = MonoFontFamily)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAnnouncementDialog) {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        var pinned by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showAnnouncementDialog = false },
            title = { Text("Publish Announcement") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                    OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Content") })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = pinned, onCheckedChange = { pinned = it })
                        Text("Pin Announcement")
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.publishAnnouncement(title, body, pinned); showAnnouncementDialog = false }, enabled = title.isNotBlank() && body.isNotBlank()) { Text("Publish") }
            },
            dismissButton = { TextButton(onClick = { showAnnouncementDialog = false }) { Text("Cancel") } }
        )
    }

    if (showPollDialog) {
        var title by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("GENERAL") }
        var opt1 by remember { mutableStateOf("") }
        var opt2 by remember { mutableStateOf("") }
        var opt3 by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPollDialog = false },
            title = { Text("Create General Poll (No Match Date Polls)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Poll Question") })
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("GENERAL", "JERSEY_SELECTION", "MAN_OF_THE_MATCH").forEach { cat ->
                            FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat.take(7), style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                    OutlinedTextField(value = opt1, onValueChange = { opt1 = it }, label = { Text("Option 1") }, singleLine = true)
                    OutlinedTextField(value = opt2, onValueChange = { opt2 = it }, label = { Text("Option 2") }, singleLine = true)
                    OutlinedTextField(value = opt3, onValueChange = { opt3 = it }, label = { Text("Option 3 (Optional)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.createGeneralPoll(title, category, listOf(opt1, opt2, opt3), false, 3); showPollDialog = false },
                    enabled = title.isNotBlank() && opt1.isNotBlank() && opt2.isNotBlank()
                ) { Text("Publish Poll") }
            },
            dismissButton = { TextButton(onClick = { showPollDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun GalleryPane(
    snapshot: TeamWorkspaceSnapshot,
    strings: UiStrings,
    isAdmin: Boolean,
    viewModel: ClubViewModel
) {
    val context = LocalContext.current
    var caption by remember { mutableStateOf("") }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.uploadGalleryMedia(context, it, "IMAGE", caption.ifBlank { "Club Moment" }) }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.uploadGalleryMedia(context, it, "VIDEO", caption.ifBlank { "Match Highlight" }) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isAdmin) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = caption, onValueChange = { caption = it }, label = { Text("Media Caption") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                modifier = Modifier.weight(1f)
                            ) { Text("Upload Photo") }
                            FilledTonalButton(
                                onClick = { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                                modifier = Modifier.weight(1f)
                            ) { Text("Upload Video") }
                        }
                    }
                }
            }
        }
        if (snapshot.galleryItems.isEmpty()) {
            item { EmptyStateCard(Icons.Default.PhotoLibrary, strings.noData) }
        } else {
            items(snapshot.galleryItems, key = { it.id }) { item ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (item.mediaType == "IMAGE") {
                            AsyncImage(
                                model = item.mediaUrl,
                                contentDescription = item.caption ?: "Team photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(12.dp))
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.PlayCircle, null, tint = AthleticGreen, modifier = Modifier.size(36.dp))
                                Text("Video Clip: ${item.caption ?: "Match Highlight"}", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (!item.caption.isNullOrBlank()) Text(item.caption, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun EquipmentAndStatsPane(
    snapshot: TeamWorkspaceSnapshot,
    strings: UiStrings,
    isAdmin: Boolean,
    viewModel: ClubViewModel
) {
    var showAddAsset by remember { mutableStateOf(false) }
    val totalMatches = snapshot.matchResults.size
    val wins = snapshot.matchResults.count { it.outcome == "WIN" }
    val draws = snapshot.matchResults.count { it.outcome == "DRAW" }
    val losses = snapshot.matchResults.count { it.outcome == "LOSS" }
    val gf = snapshot.matchResults.sumOf { it.teamScore }
    val ga = snapshot.matchResults.sumOf { it.opponentScore }
    val winPct = if (totalMatches > 0) (wins * 100) / totalMatches else 0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(title = strings.matchResults)
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Matches: $totalMatches | W: $wins | D: $draws | L: $losses", fontWeight = FontWeight.Bold)
                    Text("Goals: $gf GF / $ga GA | Win Rate: $winPct%", fontFamily = MonoFontFamily)
                }
            }
        }

        item {
            SectionHeader(
                title = strings.equipmentAndJerseys,
                actionLabel = if (isAdmin) "+ Add Asset" else null,
                onAction = if (isAdmin) { { showAddAsset = true } } else null
            )
        }

        items(snapshot.equipment, key = { it.id }) { eq ->
            val isLowStock = eq.availableQuantity <= eq.lowStockThreshold
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${eq.name} (${eq.category})", fontWeight = FontWeight.Bold)
                        StatusPill("${eq.availableQuantity}/${eq.quantity} Avail", if (isLowStock) StatusError else AthleticGreen)
                    }
                    Text("Condition: ${eq.condition} • Location: ${eq.storageLocation}", style = MaterialTheme.typography.bodySmall)
                    if (isAdmin && eq.availableQuantity > 0) {
                        OutlinedButton(onClick = { viewModel.checkoutEquipment(eq, snapshot.currentMember.id, 1) }) {
                            Text("Checkout 1 Unit")
                        }
                    }
                }
            }
        }

        if (snapshot.equipmentAssignments.isNotEmpty()) {
            item { SectionHeader(title = "Inventory Checkout History") }
            items(snapshot.equipmentAssignments, key = { it.id }) { asg ->
                val eqName = snapshot.equipment.firstOrNull { it.id == asg.equipmentId }?.name ?: "Equipment"
                val mName = snapshot.members.firstOrNull { it.id == asg.memberId }?.fullName ?: "Member"
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("$eqName -> $mName (${asg.quantity})", fontWeight = FontWeight.SemiBold)
                        Text("Status: ${asg.status} • Date: ${asg.checkoutDate}", style = MaterialTheme.typography.bodySmall)
                        if (isAdmin && asg.status == "CHECKED_OUT") {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick = { viewModel.returnOrReportEquipment(asg, "RETURNED") }) { Text("Return") }
                                TextButton(onClick = { viewModel.returnOrReportEquipment(asg, "DAMAGED") }) { Text("Damaged", color = StatusWarning) }
                                TextButton(onClick = { viewModel.returnOrReportEquipment(asg, "LOST") }) { Text("Lost", color = StatusError) }
                            }
                        }
                    }
                }
            }
        }

        if (snapshot.jerseys.isNotEmpty()) {
            item { SectionHeader(title = "Jerseys") }
            items(snapshot.jerseys, key = { it.id }) { j ->
                val assignee = snapshot.members.firstOrNull { it.id == j.assignedMemberId }?.fullName ?: "Unassigned"
                ListItem(
                    headlineContent = { Text("Jersey #${j.jerseyNumber} (Size ${j.size})") },
                    supportingContent = { Text("Assigned: $assignee • Condition: ${j.condition}") }
                )
            }
        }
    }

    if (showAddAsset) {
        var isJersey by remember { mutableStateOf(false) }
        var f1 by remember { mutableStateOf("") }
        var f2 by remember { mutableStateOf("") }
        var f3 by remember { mutableStateOf("1") }
        AlertDialog(
            onDismissRequest = { showAddAsset = false },
            title = { Text("Add Equipment or Jersey") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !isJersey, onClick = { isJersey = false }, label = { Text("Equipment") })
                        FilterChip(selected = isJersey, onClick = { isJersey = true }, label = { Text("Jersey") })
                    }
                    OutlinedTextField(value = f1, onValueChange = { f1 = it }, label = { Text(if (isJersey) "Size (L/XL)" else "Equipment Name") }, singleLine = true)
                    OutlinedTextField(value = f2, onValueChange = { f2 = it }, label = { Text(if (isJersey) "Jersey Number" else "Category") }, singleLine = true)
                    if (!isJersey) {
                        OutlinedTextField(value = f3, onValueChange = { f3 = it }, label = { Text("Quantity") }, singleLine = true)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.addEquipmentOrJersey(isJersey, f1, f2, if (isJersey) "" else f3, 0.0, "Club Room"); showAddAsset = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAddAsset = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun WhatsAppAndNotificationsPane(
    snapshot: TeamWorkspaceSnapshot,
    strings: UiStrings,
    isAdmin: Boolean,
    viewModel: ClubViewModel
) {
    val context = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var messagePreview by remember { mutableStateOf("JSC Match & Contribution Update: Please confirm your availability before Thursday 6 PM.") }
    var targetPhone by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            item {
                OutlinedButton(
                    onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.NotificationsActive, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Enable Push Notifications (FCM)")
                }
            }
        }

        if (isAdmin) {
            item {
                SectionHeader(title = strings.whatsappAutomation)
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = targetPhone,
                            onValueChange = { targetPhone = it },
                            label = { Text("Recipient WhatsApp (+880...)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = messagePreview,
                            onValueChange = { messagePreview = it },
                            label = { Text("Message / Group Report") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    viewModel.sendWhatsAppAutomation(targetPhone.ifBlank { "TEAM_GROUP" }, null, "match_update", messagePreview)
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Auto Dispatch", maxLines = 1) }
                            FilledTonalButton(
                                onClick = {
                                    ExternalIntents.shareToWhatsApp(context, messagePreview, targetPhone.ifBlank { null })
                                    viewModel.logManualWhatsAppShare(targetPhone.ifBlank { "WHATSAPP_GROUP" }, messagePreview)
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text(strings.shareToWhatsApp, maxLines = 1) }
                        }
                    }
                }
            }

            if (snapshot.whatsappLogs.isNotEmpty()) {
                item { SectionHeader(title = "WhatsApp Delivery Logs") }
                items(snapshot.whatsappLogs.take(8), key = { it.id }) { log ->
                    ListItem(
                        headlineContent = { Text("${log.recipientPhone} • ${log.templateName}") },
                        supportingContent = { Text(log.messagePreview, maxLines = 1) },
                        trailingContent = { StatusPill(log.deliveryStatus, if (log.deliveryStatus == "FAILED") StatusError else AthleticGreen) }
                    )
                }
            }
        }

        item { SectionHeader(title = strings.notifications) }
        if (snapshot.notifications.isEmpty()) {
            item { EmptyStateCard(Icons.Default.NotificationsNone, strings.noData) }
        } else {
            items(snapshot.notifications, key = { it.id }) { n ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(n.title, fontWeight = if (n.isRead) FontWeight.Normal else FontWeight.Bold)
                            Text(n.body, style = MaterialTheme.typography.bodySmall)
                        }
                        if (!n.isRead) {
                            TextButton(onClick = { viewModel.markNotificationRead(n.id) }) { Text("Read") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsAndAuditPane(
    uiState: ClubUiState,
    snapshot: TeamWorkspaceSnapshot,
    strings: UiStrings,
    viewModel: ClubViewModel
) {
    val isAdmin = uiState.isSuperAdmin
    var fullName by remember { mutableStateOf(snapshot.currentMember.fullName) }
    var mobile by remember { mutableStateOf(snapshot.currentMember.mobileNumber) }
    var position by remember { mutableStateOf(snapshot.currentMember.preferredPosition) }
    var whatsappEnabled by remember { mutableStateOf(snapshot.settings.whatsappEnabled) }
    var viewOthersAvail by remember { mutableStateOf(snapshot.settings.allowMembersViewOthersAvailability) }
    var deadlineHours by remember { mutableStateOf(snapshot.settings.availabilityDeadlineHoursBefore.toString()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(title = strings.languageLabel)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                AppLanguage.entries.forEachIndexed { idx, lang ->
                    SegmentedButton(
                        selected = uiState.language == lang,
                        onClick = { viewModel.setLanguage(lang) },
                        shape = SegmentedButtonDefaults.itemShape(idx, AppLanguage.entries.size),
                        modifier = Modifier.testTag("settings_lang_${lang.code}")
                    ) { Text(lang.displayLabel) }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Dark Theme", style = MaterialTheme.typography.titleSmall)
                Switch(checked = uiState.session.darkMode, onCheckedChange = { viewModel.setDarkMode(it) })
            }
        }

        item {
            SectionHeader(title = "Personal Profile")
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text(strings.fullName) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = mobile, onValueChange = { mobile = it }, label = { Text(strings.mobileNumber) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = position, onValueChange = { position = it }, label = { Text("Playing Position") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(
                        onClick = {
                            viewModel.updateOwnProfile(fullName, mobile, mobile, snapshot.currentMember.dateOfBirth, snapshot.currentMember.jerseySize, position, snapshot.currentMember.emergencyContact)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(strings.save) }
                }
            }
        }

        if (isAdmin) {
            item {
                SectionHeader(title = "Team Settings (Super Admin)")
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Enable WhatsApp Business API")
                            Switch(checked = whatsappEnabled, onCheckedChange = { whatsappEnabled = it })
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Members View Team Availability")
                            Switch(checked = viewOthersAvail, onCheckedChange = { viewOthersAvail = it })
                        }
                        OutlinedTextField(value = deadlineHours, onValueChange = { deadlineHours = it }, label = { Text("Availability Deadline (Hours Before Match)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Button(
                            onClick = { viewModel.updateTeamSettings(whatsappEnabled, viewOthersAvail, deadlineHours.toIntOrNull() ?: 18) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Save Team Settings") }
                    }
                }
            }

            item { SectionHeader(title = strings.auditLogs) }
            items(snapshot.auditLogs.take(20), key = { it.id }) { log ->
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("${log.actionType} • ${log.entityType}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        Text("${log.createdAt.take(19)} • ${log.remarks ?: log.entityId}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { viewModel.signOut() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError),
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("sign_out_button")
            ) {
                Icon(Icons.Default.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text(strings.signOut)
            }
        }
    }
}
