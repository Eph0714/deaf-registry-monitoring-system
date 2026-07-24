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

data class UserDto(
    val id: Int,
    val name: String,
    val email: String,
    val role: String,
    @SerializedName("teacher_id") val teacherId: Int?,
    @SerializedName("is_active") val isActive: Int? = 1,
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
    val is_active: Int
)

data class ResetPasswordRequest(val newPassword: String)

data class PendingDeviceDto(
    val id: Int,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("device_label") val deviceLabel: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("user_name") val userName: String,
    @SerializedName("user_email") val userEmail: String
)
