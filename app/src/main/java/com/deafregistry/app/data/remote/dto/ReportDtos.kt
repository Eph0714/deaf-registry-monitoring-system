package com.deafregistry.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SummaryDto(val total: Int)
data class ByMunicipalityDto(val municipality: String, val total: Int)
data class ByMunicipalityStatusDto(
    val municipality: String,
    val bs: Int,
    val rv: Int,
    val transferred: Int,
    val unlocated: Int
)
data class ByBarangayDto(val municipality: String, val barangay: String, val total: Int)
data class ByGenderDto(val gender: String, val total: Int)
data class BySkillDto(@SerializedName("skill_level") val skillLevel: String, val total: Int)
data class ByStatusDto(@SerializedName("monitoring_status") val monitoringStatus: String, val total: Int)
data class ByConductorDto(val conductor: String, val total: Int)

data class RecentVisitDto(
    val id: Int,
    @SerializedName("visit_datetime") val visitDateTime: String,
    @SerializedName("conductor_name") val conductorName: String?,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("deaf_individual_id") val deafIndividualId: Int,
    val uuid: String
)

data class NotVisitedDto(
    val id: Int,
    val uuid: String,
    @SerializedName("full_name") val fullName: String,
    val municipality: String,
    val barangay: String,
    @SerializedName("last_visit") val lastVisit: String?
)
