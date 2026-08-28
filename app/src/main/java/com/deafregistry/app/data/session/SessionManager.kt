package com.deafregistry.app.data.session

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.deafregistry.app.data.remote.dto.UserDto
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom

data class Session(
    val token: String,
    val userId: Int,
    val name: String,
    val email: String,
    val username: String,
    val role: String,
    val teacherId: Int?,
    val photoUrl: String?
)

class SessionManager(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "deaf_registry_session",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _session = MutableStateFlow<Session?>(null)
    val session = _session.asStateFlow()

    // Deliberately in-memory only (not persisted to prefs) - true only for the remainder of this
    // app process's lifetime right after an explicit Logout, so the Login screen can suppress
    // Remember Password auto-fill just this once. A real app relaunch always starts with this
    // false, which is what lets auto-fill keep working on a normal cold start.
    private var loggedOutThisSession = false

    init {
        _session.value = loadFromPrefs()
    }

    private fun loadFromPrefs(): Session? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val userId = prefs.getInt(KEY_USER_ID, -1)
        if (userId == -1) return null
        return Session(
            token = token,
            userId = userId,
            name = prefs.getString(KEY_NAME, "") ?: "",
            email = prefs.getString(KEY_EMAIL, "") ?: "",
            username = prefs.getString(KEY_USERNAME, "") ?: "",
            role = prefs.getString(KEY_ROLE, "conductor") ?: "conductor",
            teacherId = prefs.getInt(KEY_TEACHER_ID, -1).takeIf { it != -1 },
            photoUrl = prefs.getString(KEY_PHOTO_URL, null)
        )
    }

    fun save(token: String, user: UserDto) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putInt(KEY_USER_ID, user.id)
            .putString(KEY_NAME, user.name)
            .putString(KEY_EMAIL, user.email)
            .putString(KEY_USERNAME, user.username ?: "")
            .putString(KEY_ROLE, user.role)
            .putInt(KEY_TEACHER_ID, user.teacherId ?: -1)
            .putString(KEY_PHOTO_URL, user.photoUrl)
            .apply()
        _session.value = loadFromPrefs()
    }

    /** Updates the stored profile fields (e.g. after a photo upload or a sync-time refresh) without touching the token. */
    fun updateProfile(user: UserDto) {
        if (prefs.getString(KEY_TOKEN, null) == null) return
        prefs.edit()
            .putString(KEY_NAME, user.name)
            .putString(KEY_EMAIL, user.email)
            .putString(KEY_USERNAME, user.username ?: "")
            .putString(KEY_ROLE, user.role)
            .putInt(KEY_TEACHER_ID, user.teacherId ?: -1)
            .putString(KEY_PHOTO_URL, user.photoUrl)
            .apply()
        _session.value = loadFromPrefs()
    }

    /**
     * Clears the active session only - deliberately NOT a full prefs.clear(), since that would
     * also wipe the remembered accounts, which must survive logout for "Remember password" to
     * actually remember anything across a logout/login cycle - and so a different remembered
     * account can be selected on the next login.
     */
    fun clear() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_NAME)
            .remove(KEY_EMAIL)
            .remove(KEY_USERNAME)
            .remove(KEY_ROLE)
            .remove(KEY_TEACHER_ID)
            .remove(KEY_PHOTO_URL)
            .apply()
        _session.value = null
        loggedOutThisSession = true
    }

    /**
     * True exactly once right after an explicit Logout (and only within this app process's
     * lifetime) - reading it also resets it to false, so it doesn't keep suppressing auto-fill
     * beyond the one Login screen visit that follows a logout. The Login screen uses this to
     * decide whether to skip Remember Password auto-fill: populating the form again immediately
     * after Logout would let a different person standing at the device log back in as the
     * previous user without typing anything.
     */
    fun consumeLoggedOutFlag(): Boolean {
        val was = loggedOutThisSession
        loggedOutThisSession = false
        return was
    }

    fun currentToken(): String? = _session.value?.token

    fun isLoggedIn(): Boolean = _session.value != null

    // Deliberately in-memory only, like loggedOutThisSession above - an idle timer has no meaning
    // across process restarts, and starting a fresh process is itself real activity. Updated from
    // MainActivity.onUserInteraction() (any touch/key event reaching the app) and read by a
    // periodic check in AppNavGraph plus MainActivity.onResume() (so returning to the app after
    // being away past the timeout logs out immediately, not just on the next periodic tick).
    @Volatile
    private var lastActivityAt: Long = System.currentTimeMillis()

    fun recordActivity() {
        lastActivityAt = System.currentTimeMillis()
    }

    /** True once [IDLE_TIMEOUT_MS] has passed with no recorded activity while a session is active -
     * never true when already logged out, so this can be checked unconditionally without an extra
     * isLoggedIn() guard at every call site. */
    fun shouldTimeOut(): Boolean =
        isLoggedIn() && System.currentTimeMillis() - lastActivityAt >= IDLE_TIMEOUT_MS

    fun isAdmin(): Boolean = _session.value?.role == "admin" || _session.value?.role == "super_admin"

    fun isSuperAdmin(): Boolean = _session.value?.role == "super_admin"

    /**
     * Remembers this username/password pair (added to the remembered-account set, and marked as
     * the most recently used one so it's what auto-fills the next time the login form opens).
     * Overwrites any previously stored password for the same username - this is also how a changed
     * password gets "refreshed" in storage after a successful login with Remember Password on.
     * Storage is EncryptedSharedPreferences (AES256-GCM values / AES256-SIV keys), so passwords
     * are never written to disk in plain text.
     */
    fun rememberCredentials(username: String, password: String) {
        val usernames = (prefs.getStringSet(KEY_REMEMBERED_USERNAMES, emptySet()) ?: emptySet()).toMutableSet()
        usernames.add(username)
        prefs.edit()
            .putStringSet(KEY_REMEMBERED_USERNAMES, usernames)
            .putString(passwordKey(username), password)
            .putString(KEY_LAST_REMEMBERED_USERNAME, username)
            .apply()
    }

    /** Removes a single remembered account's stored password (e.g. the user unchecked Remember
     * Password for it) - also turns off Biometric Login for that account, since it needs the
     * stored password to actually submit a login. */
    fun forgetCredentials(username: String) {
        val usernames = (prefs.getStringSet(KEY_REMEMBERED_USERNAMES, emptySet()) ?: emptySet()).toMutableSet()
        usernames.remove(username)
        val editor = prefs.edit()
            .putStringSet(KEY_REMEMBERED_USERNAMES, usernames)
            .remove(passwordKey(username))
        if (prefs.getString(KEY_LAST_REMEMBERED_USERNAME, null) == username) {
            editor.remove(KEY_LAST_REMEMBERED_USERNAME)
        }
        editor.apply()
        setBiometricEnabled(username, false)
    }

    /** Every remembered username, for the login screen's "previously remembered accounts" suggestion list. */
    fun rememberedUsernames(): List<String> =
        (prefs.getStringSet(KEY_REMEMBERED_USERNAMES, emptySet()) ?: emptySet()).sorted()

    /** The stored password for a specific remembered username, or null if that username isn't remembered. */
    fun rememberedPasswordFor(username: String): String? = prefs.getString(passwordKey(username), null)

    /** The most recently remembered username - auto-fills the login form the next time it opens. */
    fun lastRememberedUsername(): String? = prefs.getString(KEY_LAST_REMEMBERED_USERNAME, null)

    private fun passwordKey(username: String) = "$KEY_REMEMBERED_PASSWORD_PREFIX$username"

    /** Turns Biometric Login on/off for one remembered account. Only meaningful (and only ever
     * set to true) for a username that also has a remembered password, since biometric auth just
     * unlocks re-submitting that stored login rather than replacing the password entirely. */
    fun setBiometricEnabled(username: String, enabled: Boolean) {
        val usernames = (prefs.getStringSet(KEY_BIOMETRIC_ENABLED_USERNAMES, emptySet()) ?: emptySet()).toMutableSet()
        if (enabled) usernames.add(username) else usernames.remove(username)
        prefs.edit().putStringSet(KEY_BIOMETRIC_ENABLED_USERNAMES, usernames).apply()
    }

    /** True only when Biometric Login was enabled for this username AND its password is still
     * remembered - both must hold for the Login screen to actually offer the biometric shortcut. */
    fun canUseBiometricFor(username: String): Boolean {
        val enabled = (prefs.getStringSet(KEY_BIOMETRIC_ENABLED_USERNAMES, emptySet()) ?: emptySet()).contains(username)
        return enabled && rememberedPasswordFor(username) != null
    }

    /**
     * What a device needs on hand to let a conductor log back in with no connection at all:
     * a salted hash of the password (never the password itself) to verify against, plus the
     * profile fields and token from the login this was captured on. The token will usually be
     * stale by the time it's used offline - that's fine, every screen already reads local Room
     * data regardless of token validity (see SyncManager); only actual server calls made during
     * that offline session fail (with the existing "session expired" messaging), and a fresh
     * online login replaces this cache with a valid token again.
     */
    private data class OfflineCredentialCache(
        val passwordHash: String,
        val salt: String,
        val token: String,
        val userId: Int,
        val name: String,
        val email: String,
        val username: String,
        val role: String,
        val teacherId: Int?,
        val photoUrl: String?
    )

    /**
     * Captured after every successful *online* login - this is the only place a device ever
     * learns that a username/password pair is genuinely valid, so offline login can only ever
     * work for an account that has logged in online at least once on this device. Overwriting on
     * every login keeps it current with password changes and refreshed tokens.
     */
    fun cacheOfflineLogin(username: String, password: String, token: String, user: UserDto) {
        val salt = randomSalt()
        val cache = OfflineCredentialCache(
            passwordHash = hashPassword(password, salt),
            salt = salt,
            token = token,
            userId = user.id,
            name = user.name,
            email = user.email,
            username = user.username ?: username,
            role = user.role,
            teacherId = user.teacherId,
            photoUrl = user.photoUrl
        )
        prefs.edit().putString(offlineCacheKey(username), Gson().toJson(cache)).apply()
    }

    fun hasOfflineLoginCached(username: String): Boolean = prefs.contains(offlineCacheKey(username))

    /**
     * Verifies [password] against the salted hash saved by [cacheOfflineLogin] for [username],
     * and if it matches, restores that cached login as the active session (same effect as
     * [save], just from cached data instead of a fresh server response). Returns false without
     * touching the active session on any mismatch or missing cache - callers should only get
     * here after a real login attempt failed to reach the server at all.
     */
    fun tryOfflineLogin(username: String, password: String): Boolean {
        val json = prefs.getString(offlineCacheKey(username), null) ?: return false
        val cache = runCatching { Gson().fromJson(json, OfflineCredentialCache::class.java) }.getOrNull() ?: return false
        if (hashPassword(password, cache.salt) != cache.passwordHash) return false
        prefs.edit()
            .putString(KEY_TOKEN, cache.token)
            .putInt(KEY_USER_ID, cache.userId)
            .putString(KEY_NAME, cache.name)
            .putString(KEY_EMAIL, cache.email)
            .putString(KEY_USERNAME, cache.username)
            .putString(KEY_ROLE, cache.role)
            .putInt(KEY_TEACHER_ID, cache.teacherId ?: -1)
            .putString(KEY_PHOTO_URL, cache.photoUrl)
            .apply()
        _session.value = loadFromPrefs()
        return true
    }

    private fun offlineCacheKey(username: String) = "$KEY_OFFLINE_LOGIN_CACHE_PREFIX$username"

    private fun randomSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashPassword(password: String, salt: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest((salt + password).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_NAME = "name"
        private const val KEY_EMAIL = "email"
        private const val KEY_USERNAME = "username"
        private const val KEY_ROLE = "role"
        private const val KEY_TEACHER_ID = "teacher_id"
        private const val KEY_PHOTO_URL = "photo_url"
        private const val KEY_REMEMBERED_USERNAMES = "remembered_usernames"
        private const val KEY_LAST_REMEMBERED_USERNAME = "last_remembered_username"
        private const val KEY_REMEMBERED_PASSWORD_PREFIX = "remembered_password::"
        private const val KEY_BIOMETRIC_ENABLED_USERNAMES = "biometric_enabled_usernames"
        private const val KEY_OFFLINE_LOGIN_CACHE_PREFIX = "offline_login_cache::"
        const val IDLE_TIMEOUT_MS = 5 * 60 * 1000L
    }
}
