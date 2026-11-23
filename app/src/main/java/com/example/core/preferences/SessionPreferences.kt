package com.example.core.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.i18n.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "jsc_session_prefs")

data class StoredSession(
    val accessToken: String = "",
    val refreshToken: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val activeTeamId: String = "",
    val language: AppLanguage = AppLanguage.BENGALI,
    val darkMode: Boolean = false,
    val notifyMatches: Boolean = true,
    val notifyFinance: Boolean = true,
    val notifyPollsAndBirthdays: Boolean = true
)

class SessionPreferences(private val context: Context) {
    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val ACTIVE_TEAM_ID = stringPreferencesKey("active_team_id")
        val LANGUAGE = stringPreferencesKey("language")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val NOTIFY_MATCHES = booleanPreferencesKey("notify_matches")
        val NOTIFY_FINANCE = booleanPreferencesKey("notify_finance")
        val NOTIFY_POLLS = booleanPreferencesKey("notify_polls")
    }

    val sessionFlow: Flow<StoredSession> = context.dataStore.data.map { prefs ->
        StoredSession(
            accessToken = prefs[Keys.ACCESS_TOKEN].orEmpty(),
            refreshToken = prefs[Keys.REFRESH_TOKEN].orEmpty(),
            userId = prefs[Keys.USER_ID].orEmpty(),
            userEmail = prefs[Keys.USER_EMAIL].orEmpty(),
            activeTeamId = prefs[Keys.ACTIVE_TEAM_ID].orEmpty(),
            language = AppLanguage.fromCode(prefs[Keys.LANGUAGE]),
            darkMode = prefs[Keys.DARK_MODE] ?: false,
            notifyMatches = prefs[Keys.NOTIFY_MATCHES] ?: true,
            notifyFinance = prefs[Keys.NOTIFY_FINANCE] ?: true,
            notifyPollsAndBirthdays = prefs[Keys.NOTIFY_POLLS] ?: true
        )
    }

    suspend fun saveAuthSession(
        accessToken: String,
        refreshToken: String,
        userId: String,
        email: String,
        teamId: String = ""
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = accessToken
            prefs[Keys.REFRESH_TOKEN] = refreshToken
            prefs[Keys.USER_ID] = userId
            prefs[Keys.USER_EMAIL] = email
            if (teamId.isNotBlank()) prefs[Keys.ACTIVE_TEAM_ID] = teamId
        }
    }

    suspend fun saveActiveTeamId(teamId: String) {
        context.dataStore.edit { prefs -> prefs[Keys.ACTIVE_TEAM_ID] = teamId }
    }

    suspend fun saveLanguage(language: AppLanguage) {
        context.dataStore.edit { prefs -> prefs[Keys.LANGUAGE] = language.code }
    }

    suspend fun saveDarkMode(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.DARK_MODE] = enabled }
    }

    suspend fun saveNotificationPrefs(matches: Boolean, finance: Boolean, polls: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NOTIFY_MATCHES] = matches
            prefs[Keys.NOTIFY_FINANCE] = finance
            prefs[Keys.NOTIFY_POLLS] = polls
        }
    }

    suspend fun clearAuthSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.ACCESS_TOKEN)
            prefs.remove(Keys.REFRESH_TOKEN)
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.USER_EMAIL)
            prefs.remove(Keys.ACTIVE_TEAM_ID)
        }
    }
}
