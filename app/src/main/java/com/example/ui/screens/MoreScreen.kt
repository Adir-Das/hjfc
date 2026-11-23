package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.i18n.UiStrings
import com.example.data.repository.TeamWorkspaceSnapshot
import com.example.ui.viewmodel.ClubUiState
import com.example.ui.viewmodel.ClubViewModel

private enum class MoreSection {
    HUB, ANNOUNCEMENTS_POLLS, GALLERY, EQUIPMENT_STATS, NOTIFICATIONS_WHATSAPP, SETTINGS_AUDIT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    uiState: ClubUiState,
    snapshot: TeamWorkspaceSnapshot,
    strings: UiStrings,
    viewModel: ClubViewModel
) {
    var currentSection by remember { mutableStateOf(MoreSection.HUB) }

    BackHandler(enabled = currentSection != MoreSection.HUB) {
        currentSection = MoreSection.HUB
    }

    if (currentSection == MoreSection.HUB) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Text(strings.navMore, style = MaterialTheme.typography.headlineSmall) }
            item {
                MoreMenuCard(
                    title = "${strings.announcements} & ${strings.teamPolls}",
                    subtitle = "Notices, pinned announcements, general team polls",
                    icon = Icons.Default.Campaign,
                    testTag = "more_announcements_card",
                    onClick = { currentSection = MoreSection.ANNOUNCEMENTS_POLLS }
                )
            }
            item {
                MoreMenuCard(
                    title = strings.gallery,
                    subtitle = "Match photos, highlight videos, Supabase Storage",
                    icon = Icons.Default.PhotoLibrary,
                    testTag = "more_gallery_card",
                    onClick = { currentSection = MoreSection.GALLERY }
                )
            }
            item {
                MoreMenuCard(
                    title = "${strings.equipmentAndJerseys} & ${strings.matchResults}",
                    subtitle = "Jerseys, checkout/return, W/D/L stats & Man of the Match",
                    icon = Icons.Default.Inventory,
                    testTag = "more_equipment_card",
                    onClick = { currentSection = MoreSection.EQUIPMENT_STATS }
                )
            }
            item {
                MoreMenuCard(
                    title = "${strings.notifications} & ${strings.whatsappAutomation}",
                    subtitle = "FCM alerts, WhatsApp Business automation & Share to WhatsApp",
                    icon = Icons.Default.Notifications,
                    testTag = "more_notifications_card",
                    onClick = { currentSection = MoreSection.NOTIFICATIONS_WHATSAPP }
                )
            }
            item {
                MoreMenuCard(
                    title = "${strings.settings} & ${strings.auditLogs}",
                    subtitle = "Bengali/English, dark mode, profile, team settings, audit trail",
                    icon = Icons.Default.Settings,
                    testTag = "more_settings_card",
                    onClick = { currentSection = MoreSection.SETTINGS_AUDIT }
                )
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        when (currentSection) {
                            MoreSection.ANNOUNCEMENTS_POLLS -> strings.announcements
                            MoreSection.GALLERY -> strings.gallery
                            MoreSection.EQUIPMENT_STATS -> strings.equipmentAndJerseys
                            MoreSection.NOTIFICATIONS_WHATSAPP -> strings.notifications
                            MoreSection.SETTINGS_AUDIT -> strings.settings
                            MoreSection.HUB -> strings.navMore
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { currentSection = MoreSection.HUB }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
            when (currentSection) {
                MoreSection.ANNOUNCEMENTS_POLLS -> AnnouncementsAndPollsPane(snapshot, strings, uiState.isSuperAdmin, viewModel)
                MoreSection.GALLERY -> GalleryPane(snapshot, strings, uiState.isSuperAdmin, viewModel)
                MoreSection.EQUIPMENT_STATS -> EquipmentAndStatsPane(snapshot, strings, uiState.isSuperAdmin, viewModel)
                MoreSection.NOTIFICATIONS_WHATSAPP -> WhatsAppAndNotificationsPane(snapshot, strings, uiState.isSuperAdmin, viewModel)
                MoreSection.SETTINGS_AUDIT -> SettingsAndAuditPane(uiState, snapshot, strings, viewModel)
                MoreSection.HUB -> Unit
            }
        }
    }
}

@Composable
private fun MoreMenuCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        ListItem(
            leadingContent = { Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.secondary) },
            headlineContent = { Text(title) },
            supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
        )
    }
}
