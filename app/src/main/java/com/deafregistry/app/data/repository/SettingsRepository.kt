package com.deafregistry.app.data.repository

import android.content.SharedPreferences
import com.deafregistry.app.data.remote.ApiService
import com.deafregistry.app.data.remote.dto.AppVersionDto
import com.deafregistry.app.data.remote.dto.OverdueDaysDto
import com.deafregistry.app.data.remote.dto.ThemeDto
import com.deafregistry.app.ui.theme.AppThemeOption
import com.deafregistry.app.ui.theme.ThemeState

class SettingsRepository(
    private val api: ApiService,
    private val prefs: SharedPreferences
) {
    suspend fun refreshOverdueDays(): Int {
        val remote = api.getOverdueDays().overdueDays
        prefs.edit().putInt(KEY_OVERDUE_DAYS, remote).apply()
        return remote
    }

    suspend fun updateOverdueDays(days: Int): Int {
        val response = api.updateOverdueDays(OverdueDaysDto(days)).overdueDays
        prefs.edit().putInt(KEY_OVERDUE_DAYS, response).apply()
        return response
    }

    fun cachedOverdueDays(): Long = prefs.getInt(KEY_OVERDUE_DAYS, DEFAULT_OVERDUE_DAYS).toLong()

    suspend fun getLatestAppVersion(): AppVersionDto = api.getAppVersion()

    suspend fun updateLatestAppVersion(versionCode: Int, versionName: String, apkUrl: String, releaseNotes: String?): AppVersionDto =
        api.updateAppVersion(AppVersionDto(versionCode, versionName, apkUrl, releaseNotes))

    /** Reads the last-known theme from disk into ThemeState so the very first frame (even the
     * pre-login screen) renders correctly - call once at app startup, before setContent. */
    fun applyCachedTheme() {
        ThemeState.current = AppThemeOption.fromKey(prefs.getString(KEY_THEME, null))
    }

    /** Fetches the admin-configured theme from the server and applies it live. Safe to call on every pull/sync. */
    suspend fun refreshTheme(): AppThemeOption {
        val remote = AppThemeOption.fromKey(api.getTheme().theme)
        prefs.edit().putString(KEY_THEME, remote.key).apply()
        ThemeState.current = remote
        return remote
    }

    /** Admin/Super Admin only - sets the theme for every user of the app, not just this device. */
    suspend fun updateTheme(option: AppThemeOption): AppThemeOption {
        val remote = AppThemeOption.fromKey(api.updateTheme(ThemeDto(option.key)).theme)
        prefs.edit().putString(KEY_THEME, remote.key).apply()
        ThemeState.current = remote
        return remote
    }

    companion object {
        private const val DEFAULT_OVERDUE_DAYS = 30
        private const val KEY_OVERDUE_DAYS = "overdue_days"
        private const val KEY_THEME = "app_theme"
    }
}
