package com.deafregistry.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class MunicipalityDto(
    val id: Int,
    val name: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("deaf_count") val deafCount: Int
)

data class BarangayDto(
    val id: Int,
    val name: String,
    @SerializedName("municipality_id") val municipalityId: Int,
    @SerializedName("municipality_name") val municipalityName: String,
    @SerializedName("updated_at") val updatedAt: String
)

data class TeacherDto(
    val id: Int,
    val name: String,
    @SerializedName("contact_number") val contactNumber: String?,
    @SerializedName("assigned_count") val assignedCount: Int,
    @SerializedName("updated_at") val updatedAt: String
)

/** Lightweight, unauthenticated shape returned by /municipalities/public and /barangays/public -
 * used only by the pre-login Sign Up form's dropdowns (no deaf_count/updated_at, unlike the
 * authenticated MunicipalityDto/BarangayDto above - registry size isn't exposed pre-login). */
data class PublicMunicipalityDto(val id: Int, val name: String)
data class PublicBarangayDto(val id: Int, val name: String)

data class NameRequest(val name: String)
data class BarangayRequest(val name: String, val municipality_id: Int)
data class TeacherRequest(val name: String, val contact_number: String?)

data class AssignmentHistoryDto(
    val id: Int,
    @SerializedName("deaf_individual_id") val deafIndividualId: Int,
    @SerializedName("old_teacher_id") val oldTeacherId: Int?,
    @SerializedName("old_teacher_name") val oldTeacherName: String?,
    @SerializedName("new_teacher_id") val newTeacherId: Int?,
    @SerializedName("new_teacher_name") val newTeacherName: String?,
    @SerializedName("changed_by_name") val changedByName: String?,
    val reason: String?,
    @SerializedName("changed_at") val changedAt: String
)

data class BulkReassignRequest(val from_teacher_id: Int, val to_teacher_id: Int, val reason: String? = null)
data class BulkReassignResponse(@SerializedName("reassigned_count") val reassignedCount: Int)

data class OverdueDaysDto(@SerializedName("overdue_days") val overdueDays: Int)

data class AppVersionDto(
    @SerializedName("version_code") val versionCode: Int,
    @SerializedName("version_name") val versionName: String?,
    @SerializedName("apk_url") val apkUrl: String?,
    @SerializedName("release_notes") val releaseNotes: String?
)

data class ThemeDto(@SerializedName("theme") val theme: String)

data class LocationShareTtlDto(@SerializedName("location_share_ttl_minutes") val locationShareTtlMinutes: Int)
