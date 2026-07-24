package com.deafregistry.app.data.repository

import android.os.Build
import com.deafregistry.app.data.remote.ApiService
import com.deafregistry.app.data.remote.dto.ChangePasswordRequest
import com.deafregistry.app.data.remote.dto.LoginErrorBody
import com.deafregistry.app.data.remote.dto.LoginRequest
import com.deafregistry.app.data.remote.dto.UserDto
import com.deafregistry.app.data.session.SessionManager
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.File

sealed class LoginException(message: String) : Exception(message) {
    class DevicePending(message: String) : LoginException(message)
    class DeviceRejected(message: String) : LoginException(message)
}

class AuthRepository(
    private val api: ApiService,
    private val sessionManager: SessionManager
) {
    suspend fun login(email: String, password: String) {
        val request = LoginRequest(email, password, sessionManager.deviceId(), Build.MODEL)
        try {
            val response = api.login(request)
            sessionManager.save(response.token, response.user)
        } catch (e: HttpException) {
            if (e.code() == 403) {
                val body = runCatching {
                    Gson().fromJson(e.response()?.errorBody()?.string(), LoginErrorBody::class.java)
                }.getOrNull()
                when (body?.code) {
                    "DEVICE_PENDING" -> throw LoginException.DevicePending(
                        body.message ?: "This device is awaiting administrator approval."
                    )
                    "DEVICE_REJECTED" -> throw LoginException.DeviceRejected(
                        body.message ?: "This device has been denied access. Contact your administrator."
                    )
                }
            }
            throw e
        }
    }

    suspend fun changePassword(current: String, newPassword: String) {
        api.changePassword(ChangePasswordRequest(current, newPassword))
    }

    suspend fun uploadProfilePhoto(filePath: String) {
        val file = File(filePath)
        val body = file.asRequestBody("image/*".toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("photo", file.name, body)
        api.uploadUserPhoto(part)
        refreshProfile()
    }

    /** Re-fetches the logged-in user's own record (name/role/photo/etc.) and updates the stored session. */
    suspend fun refreshProfile() {
        val session = sessionManager.session.value ?: return
        val fresh = api.me()
        sessionManager.updateProfile(
            UserDto(
                id = session.userId,
                name = fresh.name,
                email = fresh.email,
                role = fresh.role,
                teacherId = fresh.teacherId,
                photoUrl = fresh.photoUrl
            )
        )
    }

    fun logout() {
        sessionManager.clear()
    }
}
