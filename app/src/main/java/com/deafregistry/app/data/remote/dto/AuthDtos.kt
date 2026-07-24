package com.deafregistry.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    val email: String,
    val password: String,
    val device_id: String,
    val device_label: String? = null
)

data class LoginErrorBody(val code: String? = null, val message: String? = null)

data class LoginResponse(val token: String, val user: UserDto)

data class SignupRequest(
    val name: String,
    val email: String,
    val password: String,
    val contact_number: String? = null,
    val location: String? = null
)

data class MessageResponse(val message: String)

data class PendingSignupDto(
    val id: Int,
    val name: String,
    val email: String,
    @SerializedName("contact_number") val contactNumber: String?,
    val location: String?,
    @SerializedName("created_at") val createdAt: String
)

data class UserDto(
    val id: Int,
    val name: String,
    val email: String,
    val role: String,
    @SerializedName("teacher_id") val teacherId: Int?,
    @SerializedName("is_active") val isActive: Boolean? = true,
    @SerializedName("teacher_name") val teacherName: String? = null,
    @SerializedName("photo_url") val photoUrl: String? = null
)

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

data class CreateUserRequest(
    val name: String,
    val email: String,
    val password: String,
    val role: String,
    val teacher_id: Int?
)

data class UpdateUserRequest(
    val name: String,
    val role: String,
    val teacher_id: Int?,
    val is_active: Boolean
)

data class ResetPasswordRequest(val newPassword: String)

data class AuditLogDto(
    val id: Int,
    val action: String,
    @SerializedName("entity_type") val entityType: String,
    @SerializedName("entity_id") val entityId: Int?,
    val details: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("user_name") val userName: String?,
    @SerializedName("user_email") val userEmail: String?
)

data class PendingDeviceDto(
    val id: Int,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("device_label") val deviceLabel: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("user_name") val userName: String,
    @SerializedName("user_email") val userEmail: String
)
