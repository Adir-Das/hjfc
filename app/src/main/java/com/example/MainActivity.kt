package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.core.i18n.StringsProvider
import com.example.ui.screens.*
import com.example.ui.theme.JournalistSportsClubTheme
import com.example.ui.viewmodel.ClubViewModel
import kotlinx.serialization.Serializable

@Serializable object RouteHome
@Serializable object RouteMembers
@Serializable object RouteMatches
@Serializable object RouteFinance
@Serializable object RouteMore

private data class NavTab(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: ClubViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val strings = remember(uiState.language) { StringsProvider.get(uiState.language) }

            JournalistSportsClubTheme(darkTheme = uiState.session.darkMode) {
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(uiState.snackbarMessage) {
                    uiState.snackbarMessage?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        viewModel.clearSnackbar()
                    }
                }

                if (uiState.isBootstrapping) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (!uiState.isAuthenticated) {
                    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
                        Box(Modifier.padding(padding)) {
                            AuthScreen(
                                strings = strings,
                                language = uiState.language,
                                isLoading = uiState.isLoading,
                                errorMessage = uiState.errorMessage,
                                onLanguageChange = viewModel::setLanguage,
                                onSignIn = viewModel::signIn,
                                onCreateClub = viewModel::signUpCreateTeam,
                                onJoinInvite = viewModel::signUpJoinInvite,
                                onForgotPassword = viewModel::forgotPassword
                            )
                        }
                    }
                } else {
                    val navController = rememberNavController()
                    var selectedTab by remember { mutableIntStateOf(0) }
                    val tabs = listOf(
                        NavTab(RouteHome, strings.navHome, Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
                        NavTab(RouteMembers, strings.navMembers, Icons.Filled.Groups, Icons.Outlined.Groups, "nav_members"),
                        NavTab(RouteMatches, strings.navMatches, Icons.Filled.SportsSoccer, Icons.Outlined.SportsSoccer, "nav_matches"),
                        NavTab(RouteFinance, strings.navFinance, Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet, "nav_finance"),
                        NavTab(RouteMore, strings.navMore, Icons.Filled.Apps, Icons.Outlined.Apps, "nav_more")
                    )

                    fun navigateTab(idx: Int) {
                        selectedTab = idx
                        navController.navigate(tabs[idx].route) {
                            popUpTo(RouteHome) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }

                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val useNavRail = maxWidth >= 600.dp

                        Scaffold(
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            bottomBar = {
                                if (!useNavRail) {
                                    NavigationBar {
                                        tabs.forEachIndexed { idx, tab ->
                                            NavigationBarItem(
                                                selected = selectedTab == idx,
                                                onClick = { navigateTab(idx) },
                                                icon = {
                                                    Icon(
                                                        if (selectedTab == idx) tab.selectedIcon else tab.unselectedIcon,
                                                        contentDescription = tab.label
                                                    )
                                                },
                                                label = { Text(tab.label, maxLines = 1) },
                                                modifier = Modifier.testTag(tab.testTag)
                                            )
                                        }
                                    }
                                }
                            }
                        ) { innerPadding ->
                            Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                                if (useNavRail) {
                                    NavigationRail {
                                        tabs.forEachIndexed { idx, tab ->
                                            NavigationRailItem(
                                                selected = selectedTab == idx,
                                                onClick = { navigateTab(idx) },
                                                icon = {
                                                    Icon(
                                                        if (selectedTab == idx) tab.selectedIcon else tab.unselectedIcon,
                                                        contentDescription = tab.label
                                                    )
                                                },
                                                label = { Text(tab.label) },
                                                modifier = Modifier.testTag(tab.testTag)
                                            )
                                        }
                                    }
                                }

                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    val snap = uiState.snapshot
                                    when {
                                        snap == null && uiState.isLoading -> {
                                            Column(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalArrangement = Arrangement.Center,
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                CircularProgressIndicator()
                                                Spacer(Modifier.height(10.dp))
                                                Text(strings.loading)
                                            }
                                        }
                                        snap == null -> {
                                            Column(
                                                modifier = Modifier.fillMaxSize().padding(24.dp),
                                                verticalArrangement = Arrangement.Center,
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Icon(Icons.Default.CloudSync, null, modifier = Modifier.size(48.dp))
                                                Spacer(Modifier.height(10.dp))
                                                Text(uiState.errorMessage ?: strings.supabaseNotConfiguredDesc)
                                                Spacer(Modifier.height(12.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Button(onClick = { viewModel.refreshWorkspace() }) { Text(strings.retry) }
                                                    OutlinedButton(onClick = { viewModel.signOut() }) { Text(strings.signOut) }
                                                }
                                            }
                                        }
                                        else -> {
                                            NavHost(navController = navController, startDestination = RouteHome) {
                                                composable<RouteHome> {
                                                    DashboardScreen(snap, strings, viewModel::refreshWorkspace) { navigateTab(it) }
                                                }
                                                composable<RouteMembers> {
                                                    MembersScreen(snap, strings, viewModel)
                                                }
                                                composable<RouteMatches> {
                                                    MatchesScreen(snap, strings, viewModel)
                                                }
                                                composable<RouteFinance> {
                                                    FinanceScreen(snap, strings, viewModel)
                                                }
                                                composable<RouteMore> {
                                                    MoreScreen(uiState, snap, strings, viewModel)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
