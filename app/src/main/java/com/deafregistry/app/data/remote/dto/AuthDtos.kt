package com.deafregistry.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginErrorBody(val code: String? = null, val message: String? = null)

data class LoginResponse(val token: String, val user: UserDto)

data class SignupRequest(
    val name: String,
    val email: String,
    val username: String,
    val password: String,
    val contact_number: String? = null,
    val location: String? = null
)

data class MessageResponse(val message: String)

data class PendingSignupDto(
    val id: Int,
    val name: String,
    val email: String,
    val username: String?,
    @SerializedName("contact_number") val contactNumber: String?,
    val location: String?,
    @SerializedName("created_at") val createdAt: String
)

data class UserDto(
    val id: Int,
    val name: String,
    val email: String,
    val username: String? = null,
    val role: String,
    @SerializedName("teacher_id") val teacherId: Int?,
    @SerializedName("is_active") val isActive: Boolean? = true,
    @SerializedName("teacher_name") val teacherName: String? = null,
    @SerializedName("photo_url") val photoUrl: String? = null,
    @SerializedName("last_login_at") val lastLoginAt: String? = null
)

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

data class CreateUserRequest(
    val name: String,
    val email: String,
    val username: String,
    val password: String,
    val role: String,
    val teacher_id: Int?
)

data class UpdateUserRequest(
    val name: String,
    val username: String,
    val role: String,
    val teacher_id: Int?,
    val is_active: Boolean
)

data class ForgotPasswordRequest(
    val username: String,
    val note: String? = null
)

data class PasswordResetRequestDto(
    val id: Int,
    val username: String,
    val note: String?,
    val status: String,
    @SerializedName("requested_at") val requestedAt: String
)

data class ResolvePasswordResetRequest(@SerializedName("new_password") val newPassword: String? = null)

data class ResetPasswordRequest(val newPassword: String)

data class ShareLocationRequest(val latitude: Double, val longitude: Double)

data class ShareLocationResponse(
    @SerializedName("shared_latitude") val sharedLatitude: Double?,
    @SerializedName("shared_longitude") val sharedLongitude: Double?,
    @SerializedName("shared_location_at") val sharedLocationAt: String?
)

data class UserLocationDto(
    val id: Int,
    val name: String,
    val role: String,
    @SerializedName("shared_latitude") val sharedLatitude: Double,
    @SerializedName("shared_longitude") val sharedLongitude: Double,
    @SerializedName("shared_location_at") val sharedLocationAt: String
)

data class AuditLogDto(
    val id: Int,
    val action: String,
    @SerializedName("entity_type") val entityType: String,
    @SerializedName("entity_id") val entityId: Int?,
    val details: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("user_name") val userName: String?,
    @SerializedName("user_email") val userEmail: String?,
    @SerializedName("user_username") val userUsername: String?
)
