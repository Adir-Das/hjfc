package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.config.AppConfig
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.UiStrings
import com.example.ui.theme.*

private enum class AuthTab { SIGN_IN, CREATE_CLUB, JOIN_INVITE, FORGOT }

@Composable
fun AuthScreen(
    strings: UiStrings,
    language: AppLanguage,
    isLoading: Boolean,
    errorMessage: String?,
    onLanguageChange: (AppLanguage) -> Unit,
    onSignIn: (String, String) -> Unit,
    onCreateClub: (String, String, String, String, String) -> Unit,
    onJoinInvite: (String, String, String, String, String) -> Unit,
    onForgotPassword: (String) -> Unit
) {
    var activeTab by remember { mutableStateOf(AuthTab.SIGN_IN) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var clubName by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .systemBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Language pill switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            horizontalArrangement = Arrangement.End
        ) {
            SingleChoiceSegmentedButtonRow {
                AppLanguage.entries.forEachIndexed { idx, lang ->
                    SegmentedButton(
                        selected = language == lang,
                        onClick = { onLanguageChange(lang) },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = AppLanguage.entries.size),
                        modifier = Modifier.testTag("auth_lang_${lang.code}")
                    ) {
                        Text(lang.displayLabel)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Branded Hero Banner with JSC Emblem
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .height(190.dp)
                .clip(RoundedCornerShape(24.dp))
        ) {
            Image(
                painter = painterResource(R.drawable.img_club_hero_banner),
                contentDescription = strings.appTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(NavyDark.copy(alpha = 0.35f), NavyDark.copy(alpha = 0.88f))
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.img_jsc_emblem),
                    contentDescription = "JSC Logo",
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = strings.appTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = strings.clubSubtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = GoldAccent
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        if (!AppConfig.isSupabaseConfigured) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                colors = CardDefaults.cardColors(containerColor = GoldContainer)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.CloudOff, contentDescription = null, tint = NavyDark)
                    Column {
                        Text(strings.supabaseNotConfiguredTitle, fontWeight = FontWeight.Bold, color = NavyDark)
                        Text(strings.supabaseNotConfiguredDesc, style = MaterialTheme.typography.bodySmall, color = NavyDark)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Mode Switch Tabs
        ScrollableTabRow(
            selectedTabIndex = activeTab.ordinal,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            edgePadding = 0.dp
        ) {
            Tab(
                selected = activeTab == AuthTab.SIGN_IN,
                onClick = { activeTab = AuthTab.SIGN_IN },
                text = { Text(strings.loginTitle) }
            )
            Tab(
                selected = activeTab == AuthTab.CREATE_CLUB,
                onClick = { activeTab = AuthTab.CREATE_CLUB },
                text = { Text(strings.signUpCreateClub) }
            )
            Tab(
                selected = activeTab == AuthTab.JOIN_INVITE,
                onClick = { activeTab = AuthTab.JOIN_INVITE },
                text = { Text(strings.joinWithInvite) }
            )
            Tab(
                selected = activeTab == AuthTab.FORGOT,
                onClick = { activeTab = AuthTab.FORGOT },
                text = { Text(strings.forgotPassword) }
            )
        }

        Spacer(Modifier.height(16.dp))

        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (activeTab == AuthTab.CREATE_CLUB || activeTab == AuthTab.JOIN_INVITE) {
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text(strings.fullName) },
                        leadingIcon = { Icon(Icons.Default.Person, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("auth_fullname_input")
                    )
                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { mobile = it },
                        label = { Text(strings.mobileNumber) },
                        leadingIcon = { Icon(Icons.Default.Phone, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("auth_mobile_input")
                    )
                }

                if (activeTab == AuthTab.CREATE_CLUB) {
                    OutlinedTextField(
                        value = clubName,
                        onValueChange = { clubName = it },
                        label = { Text(strings.clubName) },
                        leadingIcon = { Icon(Icons.Default.Shield, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("auth_clubname_input")
                    )
                }

                if (activeTab == AuthTab.JOIN_INVITE) {
                    OutlinedTextField(
                        value = inviteCode,
                        onValueChange = { inviteCode = it.uppercase() },
                        label = { Text(strings.inviteCode) },
                        leadingIcon = { Icon(Icons.Default.QrCode, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("auth_invite_input")
                    )
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(strings.email) },
                    leadingIcon = { Icon(Icons.Default.Email, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("auth_email_input")
                )

                if (activeTab != AuthTab.FORGOT) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(strings.password) },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password"
                                )
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("auth_password_input")
                    )
                }

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = {
                        when (activeTab) {
                            AuthTab.SIGN_IN -> onSignIn(email, password)
                            AuthTab.CREATE_CLUB -> onCreateClub(email, password, fullName, mobile, clubName)
                            AuthTab.JOIN_INVITE -> onJoinInvite(email, password, fullName, mobile, inviteCode)
                            AuthTab.FORGOT -> onForgotPassword(email)
                        }
                    },
                    enabled = !isLoading && email.isNotBlank() && (activeTab == AuthTab.FORGOT || password.length >= 6),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("auth_submit_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Text(
                            when (activeTab) {
                                AuthTab.SIGN_IN -> strings.signInBtn
                                AuthTab.CREATE_CLUB, AuthTab.JOIN_INVITE -> strings.registerBtn
                                AuthTab.FORGOT -> strings.resetPasswordBtn
                            }
                        )
                    }
                }
            }
        }
    }
}
