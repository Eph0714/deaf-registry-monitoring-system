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

    // Separate, non-encrypted store for the device identity, deliberately NOT touched by
    // clear() (logout) — a device_id must survive logout/login so a returning user on the
    // same phone isn't treated as a "new device" needing re-approval every time.
    private val devicePrefs: SharedPreferences by lazy {
        context.getSharedPreferences("deaf_registry_device", Context.MODE_PRIVATE)
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
     * also wipe the remembered email/password (KEY_REMEMBERED_*), which must survive logout for
     * "Remember password" to actually remember anything across a logout/login cycle.
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

    fun isAdmin(): Boolean = _session.value?.role == "admin"

    fun saveRememberedCredentials(email: String, password: String) {
        prefs.edit()
            .putString(KEY_REMEMBERED_EMAIL, email)
            .putString(KEY_REMEMBERED_PASSWORD, password)
            .putBoolean(KEY_REMEMBER_ME, true)
            .apply()
    }

    fun clearRememberedCredentials() {
        prefs.edit()
            .remove(KEY_REMEMBERED_EMAIL)
            .remove(KEY_REMEMBERED_PASSWORD)
            .putBoolean(KEY_REMEMBER_ME, false)
            .apply()
    }

    /** Returns the remembered email/password pair, or null if "remember me" isn't enabled. */
    fun rememberedCredentials(): Pair<String, String>? {
        if (!prefs.getBoolean(KEY_REMEMBER_ME, false)) return null
        val email = prefs.getString(KEY_REMEMBERED_EMAIL, null) ?: return null
        val password = prefs.getString(KEY_REMEMBERED_PASSWORD, null) ?: return null
        return email to password
    }

    /** A persistent per-installation identifier used for device-approval login security. */
    fun deviceId(): String {
        devicePrefs.getString(KEY_DEVICE_ID, null)?.let { return it }
        val newId = java.util.UUID.randomUUID().toString()
        devicePrefs.edit().putString(KEY_DEVICE_ID, newId).apply()
        return newId
    }

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_NAME = "name"
        private const val KEY_EMAIL = "email"
        private const val KEY_ROLE = "role"
        private const val KEY_TEACHER_ID = "teacher_id"
        private const val KEY_PHOTO_URL = "photo_url"
        private const val KEY_REMEMBER_ME = "remember_me"
        private const val KEY_REMEMBERED_EMAIL = "remembered_email"
        private const val KEY_REMEMBERED_PASSWORD = "remembered_password"
        private const val KEY_DEVICE_ID = "device_id"
    }
}
