package com.deafregistry.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.deafregistry.app.data.sync.ChatBackgroundWorker
import com.deafregistry.app.data.sync.VisitDueWorker
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.util.NotificationHelper
import java.util.concurrent.TimeUnit

class DeafRegistryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        NotificationHelper.ensureChannel(this)
        schedulePeriodicWork()
    }

    /**
     * Data only ever reaches the server when the user explicitly taps Sync (see
     * DashboardViewModel.sync()) - no background/automatic push to the server exists.
     * The overdue-visit check below is local-only (reads Room, posts a notification);
     * it never talks to the network.
     */
    private fun schedulePeriodicWork() {
        val visitDueRequest = PeriodicWorkRequestBuilder<VisitDueWorker>(12, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("visit_due_work", ExistingPeriodicWorkPolicy.KEEP, visitDueRequest)

        // 15 minutes is WorkManager's floor for PeriodicWorkRequest - this is a best-effort check
        // for when the app isn't in the foreground; see ChatBackgroundWorker's doc for what it
        // can/can't reliably catch at this interval.
        val chatRequest = PeriodicWorkRequestBuilder<ChatBackgroundWorker>(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("chat_background_work", ExistingPeriodicWorkPolicy.KEEP, chatRequest)
    }
}
