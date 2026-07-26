package com.deafregistry.app.data.repository

import com.deafregistry.app.data.remote.ApiService
import com.deafregistry.app.data.remote.dto.ChatMessageDto
import com.deafregistry.app.data.remote.dto.ChatParticipantDto
import com.deafregistry.app.data.remote.dto.ChatRecurringScheduleDto
import com.deafregistry.app.data.remote.dto.ChatRecurringScheduleRequest
import com.deafregistry.app.data.remote.dto.ChatSessionDto
import com.deafregistry.app.data.remote.dto.ChatSingleScheduleDto
import com.deafregistry.app.data.remote.dto.ChatSingleScheduleRequest
import com.deafregistry.app.data.remote.dto.ChatStatusDto
import com.deafregistry.app.data.remote.dto.MarkChatNotificationsReadRequest
import com.deafregistry.app.data.remote.dto.SendChatMessageRequest

class ChatRepository(private val api: ApiService) {

    // Admin session management
    suspend fun listSessions(): List<ChatSessionDto> = api.getChatSessions()

    // The single "current" room the Chat menu shows - null when nothing is scheduled/open/closed
    // right now (see chat.controller.js::getActiveSession for the exact fallback order).
    suspend fun activeSession(): ChatSessionDto? = api.getActiveChatSession().body()

    suspend fun openSession(id: Int): ChatSessionDto = api.openChatSession(id)
    suspend fun closeSession(id: Int): ChatSessionDto = api.closeChatSession(id)
    suspend fun deleteSession(id: Int) = api.deleteChatSession(id)
    suspend fun clearMessages(id: Int) = api.clearChatMessages(id)

    // Recurring schedules
    suspend fun listRecurringSchedules(): List<ChatRecurringScheduleDto> = api.getChatRecurringSchedules()

    suspend fun createRecurringSchedule(
        name: String, description: String?, daysOfWeek: List<Int>, startTime: String, endTime: String, retentionPolicy: String
    ): ChatRecurringScheduleDto =
        api.createChatRecurringSchedule(ChatRecurringScheduleRequest(name, description, daysOfWeek, startTime, endTime, retentionPolicy))

    suspend fun updateRecurringSchedule(
        id: Int, name: String, description: String?, daysOfWeek: List<Int>, startTime: String, endTime: String,
        retentionPolicy: String, isActive: Boolean
    ): ChatRecurringScheduleDto =
        api.updateChatRecurringSchedule(id, ChatRecurringScheduleRequest(name, description, daysOfWeek, startTime, endTime, retentionPolicy, isActive))

    suspend fun deleteRecurringSchedule(id: Int) = api.deleteChatRecurringSchedule(id)

    // Single-time schedules
    suspend fun listSingleSchedules(): List<ChatSingleScheduleDto> = api.getChatSingleSchedules()

    suspend fun createSingleSchedule(
        name: String, scheduleDate: String, startTime: String, endTime: String, remarks: String?,
        retentionPolicy: String, isActive: Boolean, conflictedRecurringScheduleId: Int?
    ): ChatSingleScheduleDto =
        api.createChatSingleSchedule(
            ChatSingleScheduleRequest(name, scheduleDate, startTime, endTime, remarks, retentionPolicy, isActive, conflictedRecurringScheduleId)
        )

    suspend fun updateSingleSchedule(
        id: Int, name: String, scheduleDate: String, startTime: String, endTime: String, remarks: String?,
        retentionPolicy: String, isActive: Boolean, conflictedRecurringScheduleId: Int?
    ): ChatSingleScheduleDto =
        api.updateChatSingleSchedule(
            id, ChatSingleScheduleRequest(name, scheduleDate, startTime, endTime, remarks, retentionPolicy, isActive, conflictedRecurringScheduleId)
        )

    suspend fun deleteSingleSchedule(id: Int) = api.deleteChatSingleSchedule(id)

    // Status (admin dashboard + user-facing chat availability)
    suspend fun chatStatus(): ChatStatusDto = api.getChatStatus()

    // Messages
    suspend fun getMessages(sessionId: Int, afterId: Int? = null): List<ChatMessageDto> =
        api.getChatMessages(sessionId, afterId)

    suspend fun sendMessage(sessionId: Int, message: String): ChatMessageDto =
        api.sendChatMessage(sessionId, SendChatMessageRequest(message))

    suspend fun deleteMessage(sessionId: Int, messageId: Int) = api.deleteChatMessage(sessionId, messageId)
    suspend fun pinMessage(sessionId: Int, messageId: Int) = api.pinChatMessage(sessionId, messageId)
    suspend fun unpinMessage(sessionId: Int, messageId: Int) = api.unpinChatMessage(sessionId, messageId)

    suspend fun searchMessages(sessionId: Int, query: String?, date: String?): List<ChatMessageDto> =
        api.searchChatMessages(sessionId, query?.takeIf { it.isNotBlank() }, date?.takeIf { it.isNotBlank() })

    // Participants
    suspend fun getParticipants(sessionId: Int): List<ChatParticipantDto> = api.getChatParticipants(sessionId)
    suspend fun joinSession(sessionId: Int) = api.joinChatSession(sessionId)
    suspend fun leaveSession(sessionId: Int) = api.leaveChatSession(sessionId)
    suspend fun muteParticipant(sessionId: Int, userId: Int) = api.muteChatParticipant(sessionId, userId)
    suspend fun unmuteParticipant(sessionId: Int, userId: Int) = api.unmuteChatParticipant(sessionId, userId)
    suspend fun removeParticipant(sessionId: Int, userId: Int) = api.removeChatParticipant(sessionId, userId)

    // Notifications
    suspend fun getNotifications() = api.getChatNotifications()
    suspend fun markNotificationsRead(ids: List<Int>? = null) = api.markChatNotificationsRead(MarkChatNotificationsReadRequest(ids))
}
