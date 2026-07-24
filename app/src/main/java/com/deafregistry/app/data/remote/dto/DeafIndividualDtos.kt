package com.deafregistry.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class DeafIndividualDto(
    val id: Int,
    val uuid: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("photo_url") val photoUrl: String?,
    @SerializedName("birth_date") val birthDate: String?,
    val gender: String,
    @SerializedName("barangay_id") val barangayId: Int,
    @SerializedName("barangay_name") val barangayName: String,
    val purok: String?,
    @SerializedName("municipality_id") val municipalityId: Int,
    @SerializedName("municipality_name") val municipalityName: String,
    val latitude: Double?,
    val longitude: Double?,
    @SerializedName("skill_level") val skillLevel: String,
    @SerializedName("monitoring_status") val monitoringStatus: String,
    @SerializedName("assigned_teacher_id") val assignedTeacherId: Int?,
    @SerializedName("teacher_name") val teacherName: String?,
    @SerializedName("teacher_contact") val teacherContact: String?,
    @SerializedName("assigned_date") val assignedDate: String?,
    @SerializedName("contact_number") val contactNumber: String?,
    val email: String?,
    @SerializedName("marital_status") val maritalStatus: String?,
    @SerializedName("emergency_contact_name") val emergencyContactName: String?,
    @SerializedName("emergency_contact_number") val emergencyContactNumber: String?,
    val notes: String?,
    @SerializedName("updated_at") val updatedAt: String,
    val visits: List<VisitDto>? = null
)

data class DeafIndividualRequest(
    val uuid: String? = null,
    val full_name: String,
    val birth_date: String?,
    val gender: String,
    val barangay_id: Int,
    val purok: String?,
    val municipality_id: Int,
    val latitude: Double?,
    val longitude: Double?,
    val skill_level: String,
    val monitoring_status: String,
    val assigned_teacher_id: Int?,
    val assigned_date: String?,
    val contact_number: String?,
    val email: String?,
    val marital_status: String?,
    val emergency_contact_name: String?,
    val emergency_contact_number: String?,
    val notes: String?
)

data class CreateResponse(val id: Int, val uuid: String)
data class PhotoUploadResponse(@SerializedName("photo_url") val photoUrl: String)
