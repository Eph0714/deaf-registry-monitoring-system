package com.deafregistry.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
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
    }
}
