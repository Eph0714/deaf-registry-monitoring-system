package com.deafregistry.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.util.NotificationHelper

/**
 * Best-effort background check for the two chat milestones that still make sense on a coarse
 * schedule: "a new session opened" and "a session ended". WorkManager's PeriodicWorkRequest has a
 * hard 15-minute minimum interval, so this can't catch the "5 minutes remaining" warning reliably
 * (a 5-minute window can fall entirely between two 15-minute checks) - that one only fires from
 * ChatRoomViewModel's foreground poll loop while the Chat screen is actually open. This worker is
 * what covers a user who isn't currently looking at the app.
 */
class ChatBackgroundWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (ServiceLocator.sessionManager.session.value == null) return Result.success()
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

        ServiceLocator.settingsRepository.setLastNotifiedChatSession(session?.id, session?.status)
        return Result.success()
    }
}
