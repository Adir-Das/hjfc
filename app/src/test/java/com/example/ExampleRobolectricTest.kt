package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.core.config.AppConfig
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.Formatters
import com.example.core.i18n.StringsProvider
import com.example.domain.model.UserRole
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.JournalistSportsClubTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `app name and defaults match specification`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Journalist Sports Club", context.getString(R.string.app_name))
        assertEquals("Asia/Dhaka", AppConfig.DHAKA_ZONE.id)
        assertTrue(Formatters.formatBdt(400.0).startsWith("৳"))
        assertEquals(AppLanguage.BENGALI, AppLanguage.fromCode(null))
        assertEquals(UserRole.SUPER_ADMIN, UserRole.from("SUPER_ADMIN"))
    }

    @Test
    fun `auth screen renders Bengali default and switches to English`() {
        var currentLang = AppLanguage.BENGALI
        composeRule.setContent {
            val strings = StringsProvider.get(currentLang)
            JournalistSportsClubTheme {
                AuthScreen(
                    strings = strings,
                    language = currentLang,
                    isLoading = false,
                    errorMessage = null,
                    onLanguageChange = { currentLang = it },
                    onSignIn = { _, _ -> },
                    onCreateClub = { _, _, _, _, _ -> },
                    onJoinInvite = { _, _, _, _, _ -> },
                    onForgotPassword = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("auth_email_input").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_password_input").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_submit_button").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_lang_en").performClick()
        assertEquals(AppLanguage.ENGLISH, currentLang)
    }
}
