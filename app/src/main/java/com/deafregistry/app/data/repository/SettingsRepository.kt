package com.deafregistry.app.data.repository

import android.content.SharedPreferences
import com.deafregistry.app.data.remote.ApiService
import com.deafregistry.app.data.remote.dto.AppVersionDto
import com.deafregistry.app.data.remote.dto.LocationShareTtlDto
import com.deafregistry.app.data.remote.dto.OverdueDaysDto
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
     * user's local override (see setLocalThemeOverride) takes priority if one is set. */
    fun applyCachedTheme() {
        ThemeState.current = cachedLocalThemeOverride() ?: AppThemeOption.fromKey(prefs.getString(KEY_THEME, null))
    }

    /** Fetches the server's default theme (what a user sees before they've ever picked one of
     * their own) and caches it. Safe to call on every pull/sync. Does NOT touch ThemeState if a
     * local override is set - otherwise every sync would silently revert a user's personal
     * choice. Returns the effective theme (the override if one is set, otherwise the default). */
    suspend fun refreshTheme(): AppThemeOption {
        val remote = AppThemeOption.fromKey(api.getTheme().theme)
        prefs.edit().putString(KEY_THEME, remote.key).apply()
        val override = cachedLocalThemeOverride()
        if (override == null) {
            ThemeState.current = remote
        }
        return override ?: remote
    }

    /** Applies a theme choice to just this device - never calls the server, so it can't affect
     * anyone else's app. Persists across restarts and takes priority over the server default. */
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

    /** Purely local, per-device - never synced to the server (there's no per-user server-side
     * notification-preference concept anywhere else in this app either). Gates all chat push-style
     * notifications: new session opened, new message, 5-minute warning, session ended. */
    fun chatNotificationsEnabled(): Boolean = prefs.getBoolean(KEY_CHAT_NOTIFICATIONS_ENABLED, true)

    fun setChatNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CHAT_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    /** Tracks the last chat session/status this device has already notified about in the
     * background (ChatBackgroundWorker) - separate from ChatRoomViewModel's in-memory dedup,
     * since this needs to survive the app process being killed between 15-minute worker runs. */
    fun lastNotifiedChatSession(): Pair<Int, String>? {
        val id = prefs.getInt(KEY_LAST_NOTIFIED_CHAT_SESSION_ID, -1)
        val status = prefs.getString(KEY_LAST_NOTIFIED_CHAT_STATUS, null)
        return if (id != -1 && status != null) id to status else null
    }

    fun setLastNotifiedChatSession(id: Int?, status: String?) {
        prefs.edit()
            .putInt(KEY_LAST_NOTIFIED_CHAT_SESSION_ID, id ?: -1)
            .putString(KEY_LAST_NOTIFIED_CHAT_STATUS, status)
            .apply()
    }

    /** The highest chat_messages.id this device has actually seen (Chat Room screen open) -
     * drives the unread-count badge on the Dashboard's Chat tile. Global rather than per-session:
     * ids increment across the whole table regardless of which session they belong to, so a fresh
     * session's messages are automatically "unseen" without needing to track anything per-session. */
    fun lastSeenChatMessageId(): Int = prefs.getInt(KEY_LAST_SEEN_CHAT_MESSAGE_ID, 0)

    fun setLastSeenChatMessageId(id: Int) {
        if (id > lastSeenChatMessageId()) {
            prefs.edit().putInt(KEY_LAST_SEEN_CHAT_MESSAGE_ID, id).apply()
        }
    }

    /** The highest chat_messages.id ChatBackgroundWorker has already fired a "new message"
     * notification for - deliberately separate from lastSeenChatMessageId (which only advances
     * when the user actually opens Chat Room and drives the unread-count badge): a background
     * notification informs the user a message arrived, but doesn't mean they've read it, so the
     * badge must keep counting it as unread until they actually open the chat. Without this
     * separate cursor, the same unseen message(s) would re-notify on every ~15-minute worker run. */
    fun lastNotifiedChatMessageId(): Int = prefs.getInt(KEY_LAST_NOTIFIED_CHAT_MESSAGE_ID, 0)

    fun setLastNotifiedChatMessageId(id: Int) {
        if (id > lastNotifiedChatMessageId()) {
            prefs.edit().putInt(KEY_LAST_NOTIFIED_CHAT_MESSAGE_ID, id).apply()
        }
    }

    companion object {
        private const val DEFAULT_OVERDUE_DAYS = 30
        private const val KEY_OVERDUE_DAYS = "overdue_days"
        private const val KEY_THEME = "app_theme"
        private const val KEY_LOCAL_THEME_OVERRIDE = "local_theme_override"
        private const val DEFAULT_LOCATION_SHARE_TTL_MINUTES = 60
        private const val KEY_LOCATION_SHARE_TTL = "location_share_ttl_minutes"
        private const val KEY_CHAT_NOTIFICATIONS_ENABLED = "chat_notifications_enabled"
        private const val KEY_LAST_NOTIFIED_CHAT_SESSION_ID = "last_notified_chat_session_id"
        private const val KEY_LAST_NOTIFIED_CHAT_STATUS = "last_notified_chat_status"
        private const val KEY_LAST_SEEN_CHAT_MESSAGE_ID = "last_seen_chat_message_id"
        private const val KEY_LAST_NOTIFIED_CHAT_MESSAGE_ID = "last_notified_chat_message_id"
    }
}
