package com.deafregistry.app.data.session

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.deafregistry.app.data.remote.dto.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Session(
    val token: String,
    val userId: Int,
    val name: String,
    val email: String,
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
            .remove(KEY_ROLE)
            .remove(KEY_TEACHER_ID)
            .remove(KEY_PHOTO_URL)
            .apply()
        _session.value = null
    }

    fun currentToken(): String? = _session.value?.token

    fun isLoggedIn(): Boolean = _session.value != null

    fun isAdmin(): Boolean = _session.value?.role == "admin" || _session.value?.role == "super_admin"

    fun isSuperAdmin(): Boolean = _session.value?.role == "super_admin"

    /**
     * Remembers this email/password pair (added to the remembered-account set, and marked as the
     * most recently used one so it's what auto-fills the next time the login form opens).
     * Overwrites any previously stored password for the same email - this is also how a changed
     * password gets "refreshed" in storage after a successful login with Remember Password on.
     * Storage is EncryptedSharedPreferences (AES256-GCM values / AES256-SIV keys), so passwords
     * are never written to disk in plain text.
     */
    fun rememberCredentials(email: String, password: String) {
        val emails = (prefs.getStringSet(KEY_REMEMBERED_EMAILS, emptySet()) ?: emptySet()).toMutableSet()
        emails.add(email)
        prefs.edit()
            .putStringSet(KEY_REMEMBERED_EMAILS, emails)
            .putString(passwordKey(email), password)
            .putString(KEY_LAST_REMEMBERED_EMAIL, email)
            .apply()
    }

    /** Removes a single remembered account's stored password (e.g. the user unchecked Remember Password for it). */
    fun forgetCredentials(email: String) {
        val emails = (prefs.getStringSet(KEY_REMEMBERED_EMAILS, emptySet()) ?: emptySet()).toMutableSet()
        emails.remove(email)
        val editor = prefs.edit()
            .putStringSet(KEY_REMEMBERED_EMAILS, emails)
            .remove(passwordKey(email))
        if (prefs.getString(KEY_LAST_REMEMBERED_EMAIL, null) == email) {
            editor.remove(KEY_LAST_REMEMBERED_EMAIL)
        }
        editor.apply()
    }

    /** Every remembered email, for the login screen's "previously remembered accounts" suggestion list. */
    fun rememberedEmails(): List<String> =
        (prefs.getStringSet(KEY_REMEMBERED_EMAILS, emptySet()) ?: emptySet()).sorted()

    /** The stored password for a specific remembered email, or null if that email isn't remembered. */
    fun rememberedPasswordFor(email: String): String? = prefs.getString(passwordKey(email), null)

    /** The most recently remembered email - auto-fills the login form the next time it opens. */
    fun lastRememberedEmail(): String? = prefs.getString(KEY_LAST_REMEMBERED_EMAIL, null)

    private fun passwordKey(email: String) = "$KEY_REMEMBERED_PASSWORD_PREFIX$email"

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_NAME = "name"
        private const val KEY_EMAIL = "email"
        private const val KEY_ROLE = "role"
        private const val KEY_TEACHER_ID = "teacher_id"
        private const val KEY_PHOTO_URL = "photo_url"
        private const val KEY_REMEMBERED_EMAILS = "remembered_emails"
        private const val KEY_LAST_REMEMBERED_EMAIL = "last_remembered_email"
        private const val KEY_REMEMBERED_PASSWORD_PREFIX = "remembered_password::"
    }
}
