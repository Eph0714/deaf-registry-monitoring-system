package com.deafregistry.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class CalendarEventDto(
    val id: Int,
    val title: String,
    val description: String?,
    @SerializedName("event_date") val eventDate: String,
    @SerializedName("created_by") val createdBy: Int?,
    @SerializedName("created_by_name") val createdByName: String?,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
)

data class CalendarEventRequest(
    val title: String,
    val description: String?,
    val event_date: String
)
