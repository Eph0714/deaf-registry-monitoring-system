package com.deafregistry.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ChatSessionDto(
    val id: Int,
    @SerializedName("session_name") val sessionName: String,
    val description: String?,
    @SerializedName("start_datetime") val startDatetime: String,
    @SerializedName("end_datetime") val endDatetime: String,
    val status: String,
    @SerializedName("retention_policy") val retentionPolicy: String,
    @SerializedName("participant_count") val participantCount: Int? = null,
    @SerializedName("created_by") val createdBy: Int? = null,
    @SerializedName("created_by_name") val createdByName: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class ChatSessionRequest(
    val session_name: String,
    val description: String?,
    val start_datetime: String,
    val end_datetime: String,
    val retention_policy: String
)

data class ChatMessageDto(
    val id: Int,
    @SerializedName("session_id") val sessionId: Int,
    @SerializedName("user_id") val userId: Int,
    val message: String,
    @SerializedName("sent_at") val sentAt: String,
    @SerializedName("edited_at") val editedAt: String?,
    @SerializedName("is_pinned") val isPinned: Boolean,
    @SerializedName("user_name") val userName: String,
    @SerializedName("user_username") val userUsername: String?,
    @SerializedName("user_photo_url") val userPhotoUrl: String?
)

data class SendChatMessageRequest(val message: String)

data class ChatParticipantDto(
    @SerializedName("user_id") val userId: Int,
    val name: String,
    val username: String?,
    val role: String,
    @SerializedName("photo_url") val photoUrl: String?,
    @SerializedName("joined_at") val joinedAt: String,
    @SerializedName("last_active_at") val lastActiveAt: String,
    @SerializedName("is_muted") val isMuted: Boolean,
    @SerializedName("is_removed") val isRemoved: Boolean,
    @SerializedName("is_online") val isOnline: Boolean
)

data class ChatNotificationDto(
    val id: Int,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("session_id") val sessionId: Int?,
    val message: String,
    @SerializedName("sent_at") val sentAt: String,
    val status: String
)

data class MarkChatNotificationsReadRequest(val ids: List<Int>? = null)
