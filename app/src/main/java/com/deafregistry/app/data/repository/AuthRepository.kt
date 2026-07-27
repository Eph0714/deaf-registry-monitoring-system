package com.deafregistry.app.data.repository

import com.deafregistry.app.data.remote.ApiService
import com.deafregistry.app.data.remote.dto.ChangePasswordRequest
import com.deafregistry.app.data.remote.dto.ForgotPasswordRequest
import com.deafregistry.app.data.remote.dto.LoginErrorBody
import com.deafregistry.app.data.remote.dto.LoginRequest
import com.deafregistry.app.data.remote.dto.ShareLocationRequest
import com.deafregistry.app.data.remote.dto.SignupRequest
import com.deafregistry.app.data.remote.dto.UserDto
import com.deafregistry.app.data.remote.dto.UserLocationDto
import com.deafregistry.app.data.session.SessionManager
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.File

sealed class LoginException(message: String) : Exception(message) {
    class AccountPending(message: String) : LoginException(message)
    class AccountRejected(message: String) : LoginException(message)
}

class AuthRepository(
    private val api: ApiService,
    private val sessionManager: SessionManager
) {
    suspend fun login(username: String, password: String) {
        val request = LoginRequest(username, password)
        try {
            val response = api.login(request)
            sessionManager.save(response.token, response.user)
        } catch (e: HttpException) {
            if (e.code() == 403) {
                val body = runCatching {
                    Gson().fromJson(e.response()?.errorBody()?.string(), LoginErrorBody::class.java)
                }.getOrNull()
                when (body?.code) {
                    "ACCOUNT_PENDING" -> throw LoginException.AccountPending(
                        body.message ?: "Your account is awaiting administrator approval."
                    )
                    "ACCOUNT_REJECTED" -> throw LoginException.AccountRejected(
                        body.message ?: "Your registration was not approved."
                    )
                }
            }
            throw e
        }
    }

    suspend fun signup(
        name: String,
        username: String,
        password: String,
        contactNumber: String?,
        location: String?
    ): String {
        val response = api.signup(SignupRequest(name, username, password, contactNumber, location))
        return response.message
    }

    /** Submits a Forgot Password request to the admin queue - this app has no working email
     * delivery for arbitrary users, so an admin/super-admin resolves it manually instead. */
    suspend fun forgotPassword(username: String, note: String?): String {
        val response = api.forgotPassword(ForgotPasswordRequest(username, note))
        return response.message
    }

    suspend fun changePassword(current: String, newPassword: String) {
        api.changePassword(ChangePasswordRequest(current, newPassword))
    }

    suspend fun uploadProfilePhoto(filePath: String) {
        val file = File(filePath)
        val mimeType = java.net.URLConnection.guessContentTypeFromName(file.name)
            ?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
        val body = file.asRequestBody(mimeType.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("photo", file.name, body)
        api.uploadUserPhoto(part)
        refreshProfile()
    }

    /** Fresh copy of the logged-in user's own record straight from the server - used by the View
     * Profile dialog so fields like Last Login always reflect the current session, not a cached one. */
    suspend fun fetchProfile(): UserDto = api.me()

    /** Re-fetches the logged-in user's own record (name/role/photo/etc.) and updates the stored session. */
    suspend fun refreshProfile() {
        val session = sessionManager.session.value ?: return
        val fresh = api.me()
        sessionManager.updateProfile(
            UserDto(
                id = session.userId,
                name = fresh.name,
                email = fresh.email,
                username = fresh.username,
                role = fresh.role,
                teacherId = fresh.teacherId,
                photoUrl = fresh.photoUrl
            )
        )
    }

    suspend fun shareLocation(latitude: Double, longitude: Double) {
        api.shareLocation(ShareLocationRequest(latitude, longitude))
    }

    suspend fun stopSharingLocation() {
        val response = api.stopSharingLocation()
        // Response<Unit> doesn't throw on a non-2xx by itself - without this check, a failed
        // stop-sharing call would look like it succeeded and the UI would show it as no longer
        // shared while the server still has the old location on record.
        if (!response.isSuccessful) throw retrofit2.HttpException(response)
    }

    suspend fun getUserLocations(): List<UserLocationDto> = api.getUserLocations()

    /**
     * Records the logout in the server-side Audit Trail before clearing the local session - best
     * effort only (wrapped so a failed/offline request never blocks the local logout, which must
     * always succeed so the user can get back to the Login form).
     */
    suspend fun logout() {
        runCatching { api.logout() }
        sessionManager.clear()
    }
}
