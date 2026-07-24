package com.deafregistry.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class VisitDto(
    val id: Int,
    val uuid: String,
    @SerializedName("deaf_individual_id") val deafIndividualId: Int,
    @SerializedName("visit_datetime") val visitDateTime: String,
    val latitude: Double?,
    val longitude: Double?,
    @SerializedName("conductor_id") val conductorId: Int?,
    @SerializedName("conductor_name") val conductorName: String?,
    @SerializedName("conductor_teacher_name") val conductorTeacherName: String? = null,
    @SerializedName("updated_at") val updatedAt: String = "",
    val remarks: List<RemarkDto>? = null
)

data class VisitRequest(
    val uuid: String? = null,
    val latitude: Double?,
    val longitude: Double?,
    val conductor_id: Int?,
    val conductor_name: String?,
    val visit_datetime: String?
)

data class RemarkDto(
    val id: Int,
    val uuid: String,
    @SerializedName("visit_id") val visitId: Int,
    @SerializedName("user_id") val userId: Int?,
    @SerializedName("user_name") val userName: String?,
    @SerializedName("remark_text") val remarkText: String,
    @SerializedName("created_at") val createdAt: String
)

data class RemarkRequest(val remark_text: String, val uuid: String? = null)

data class RemarkUpdateRequest(val remark_text: String)
