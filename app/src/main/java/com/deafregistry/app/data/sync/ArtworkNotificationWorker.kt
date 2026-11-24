package com.deafregistry.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.util.NotificationHelper

/**
 * Best-effort background check for new Artworks notifications (a new upload/deletion request for
 * an admin to review, or a decision on a request a conductor submitted) - same always-on,
 * no-screen-required pattern as ChatBackgroundWorker/VisitDueWorker. The server's
 * GET /api/notifications only ever returns unread rows, so lastNotifiedArtworkNotificationId is
 * the cursor that stops the same unread row from re-firing a push on every ~15-minute run.
 */
class ArtworkNotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!ServiceLocator.sessionManager.isLoggedIn()) return Result.success()
        if (!ServiceLocator.settingsRepository.artworkNotificationsEnabled()) return Result.success()

        val settings = ServiceLocator.settingsRepository
        val notifications = runCatching { ServiceLocator.artworksRepository.getNotifications() }.getOrNull() ?: return Result.success()
        val unseen = notifications.filter { it.id > settings.lastNotifiedArtworkNotificationId() }
        if (unseen.isEmpty()) return Result.success()

        unseen.forEach { notification ->
            NotificationHelper.notify(
                applicationContext,
                id = 6000 + (notification.id % 1000),
                title = "Artworks",
                text = notification.message,
                channelId = NotificationHelper.ARTWORK_CHANNEL_ID
            )
        }
        settings.setLastNotifiedArtworkNotificationId(unseen.maxOf { it.id })
        return Result.success()
    }
}
