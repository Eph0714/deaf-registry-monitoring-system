package com.deafregistry.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.util.NotificationHelper

/**
 * Best-effort background check for chat milestones that still make sense on a coarse schedule:
 * "a new session opened", "a session ended", and "new message(s) received" - so a user gets
 * notified even when the Chat Room screen isn't open (matching the always-on, no-screen-required
 * pattern the app already uses for overdue-visit notifications via VisitDueWorker). WorkManager's
 * PeriodicWorkRequest has a hard 15-minute minimum interval, so this can't catch the "5 minutes
 * remaining" warning reliably (a 5-minute window can fall entirely between two 15-minute checks) -
 * that one only fires from ChatRoomViewModel's foreground poll loop while Chat is actually open.
 */
class ChatBackgroundWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val currentUserId = ServiceLocator.sessionManager.session.value?.userId ?: return Result.success()
        if (!ServiceLocator.settingsRepository.chatNotificationsEnabled()) return Result.success()

        val session = runCatching { ServiceLocator.chatRepository.activeSession() }.getOrNull()
        val previous = ServiceLocator.settingsRepository.lastNotifiedChatSession()

        if (session != null && session.status == "open" && previous?.let { it.first == session.id && it.second == "open" } != true) {
            NotificationHelper.notify(
                applicationContext, id = 4001,
                title = "Chat session opened",
                text = "\"${session.sessionName}\" is now open.",
                channelId = NotificationHelper.CHAT_CHANNEL_ID
            )
        } else if (previous?.second == "open" && (session == null || session.id != previous.first || session.status != "open")) {
            NotificationHelper.notify(
                applicationContext, id = 4002,
                title = "Chat session ended",
                text = "A chat session has ended.",
                channelId = NotificationHelper.CHAT_CHANNEL_ID
            )
        }

        if (session != null && session.status == "open") {
            val settings = ServiceLocator.settingsRepository
            val alreadyInformedUpTo = maxOf(settings.lastSeenChatMessageId(), settings.lastNotifiedChatMessageId())
            val newMessages = runCatching { ServiceLocator.chatRepository.getMessages(session.id, alreadyInformedUpTo) }.getOrNull()
            val fromOthers = newMessages?.filter { it.userId != currentUserId }
            if (!fromOthers.isNullOrEmpty()) {
                val last = fromOthers.last()
                NotificationHelper.notify(
                    applicationContext, id = 4003,
                    title = session.sessionName,
                    text = "${last.userName}: ${last.message}".take(200),
                    channelId = NotificationHelper.CHAT_CHANNEL_ID
                )
                settings.setLastNotifiedChatMessageId(newMessages.last().id)
            }
        }

        ServiceLocator.settingsRepository.setLastNotifiedChatSession(session?.id, session?.status)
        return Result.success()
    }
}
