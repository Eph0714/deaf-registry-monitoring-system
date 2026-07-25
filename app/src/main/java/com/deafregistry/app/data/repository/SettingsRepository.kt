package com.deafregistry.app.data.repository

import android.content.SharedPreferences
import com.deafregistry.app.data.remote.ApiService
import com.deafregistry.app.data.remote.dto.AppVersionDto
import com.deafregistry.app.data.remote.dto.LocationShareTtlDto
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
     * pre-login screen) renders correctly - call once at app startup, before setContent. A
     * conductor's local override (see setLocalThemeOverride) takes priority if one is set. */
    fun applyCachedTheme() {
        ThemeState.current = cachedLocalThemeOverride() ?: AppThemeOption.fromKey(prefs.getString(KEY_THEME, null))
    }

    /** Fetches the admin-configured theme from the server and caches it. Safe to call on every
     * pull/sync. Does NOT touch ThemeState if a conductor has a local override set - otherwise
     * every sync would silently revert their personal choice back to the global theme. Returns
     * the effective theme (the override if one is set, otherwise the fetched global value). */
    suspend fun refreshTheme(): AppThemeOption {
        val remote = AppThemeOption.fromKey(api.getTheme().theme)
        prefs.edit().putString(KEY_THEME, remote.key).apply()
        val override = cachedLocalThemeOverride()
        if (override == null) {
            ThemeState.current = remote
        }
        return override ?: remote
    }

    /** Admin/Super Admin only - sets the theme for every user of the app, not just this device. */
    suspend fun updateTheme(option: AppThemeOption): AppThemeOption {
        val remote = AppThemeOption.fromKey(api.updateTheme(ThemeDto(option.key)).theme)
        prefs.edit().putString(KEY_THEME, remote.key).apply()
        ThemeState.current = remote
        return remote
    }

    /** Conductor-only: applies a theme choice to just this device, without calling the server
     * (conductors aren't allowed to change the app-wide theme). Persists across restarts and
     * takes priority over the global theme until cleared. */
    fun setLocalThemeOverride(option: AppThemeOption) {
        prefs.edit().putString(KEY_LOCAL_THEME_OVERRIDE, option.key).apply()
        ThemeState.current = option
    }

    fun cachedLocalThemeOverride(): AppThemeOption? =
        prefs.getString(KEY_LOCAL_THEME_OVERRIDE, null)?.let { AppThemeOption.fromKey(it) }

    suspend fun refreshLocationShareTtl(): Int {
        val remote = api.getLocationShareTtl().locationShareTtlMinutes
        prefs.edit().putInt(KEY_LOCATION_SHARE_TTL, remote).apply()
        return remote
    }

    suspend fun updateLocationShareTtl(minutes: Int): Int {
        val response = api.updateLocationShareTtl(LocationShareTtlDto(minutes)).locationShareTtlMinutes
        prefs.edit().putInt(KEY_LOCATION_SHARE_TTL, response).apply()
        return response
    }

    fun cachedLocationShareTtl(): Int = prefs.getInt(KEY_LOCATION_SHARE_TTL, DEFAULT_LOCATION_SHARE_TTL_MINUTES)

    companion object {
        private const val DEFAULT_OVERDUE_DAYS = 30
        private const val KEY_OVERDUE_DAYS = "overdue_days"
        private const val KEY_THEME = "app_theme"
        private const val KEY_LOCAL_THEME_OVERRIDE = "local_theme_override"
        private const val DEFAULT_LOCATION_SHARE_TTL_MINUTES = 60
        private const val KEY_LOCATION_SHARE_TTL = "location_share_ttl_minutes"
    }
}
