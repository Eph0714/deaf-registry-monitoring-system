package com.deafregistry.app

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.deafregistry.app.data.sync.AutoSyncWorker
import com.deafregistry.app.data.sync.ChatBackgroundWorker
import com.deafregistry.app.data.sync.VisitDueWorker
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.TimeUnit

class DeafRegistryApp : Application() {
    // Process-lifetime scope for the connectivity watcher below. Deliberately not tied to any
    // screen/ViewModel - auto-sync must keep working (and queue itself via WorkManager) even
    // when the app has no UI on screen.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        NotificationHelper.ensureChannel(this)
        schedulePeriodicWork()
        observeConnectivityForAutoSync()
    }

    /**
     * The app is fully usable offline - every screen reads/writes Room directly and edits just
     * queue as dirty (see SyncManager). This is what gets those queued edits (and fresh server
     * data) moving again the moment a connection is available, with no user action needed:
     * the user's Sync button still exists for "sync right now and show me the result", but
     * connectivity coming back on its own is now enough to trigger the same push+pull.
     *
     * Handed off to WorkManager (AutoSyncWorker) rather than run directly on appScope, so it
     * survives the process dying mid-sync and retries with backoff on failure.
     */
    private fun observeConnectivityForAutoSync() {
        ServiceLocator.networkMonitor.observe()
            .onEach { online -> if (online) enqueueImmediateAutoSync() }
            .launchIn(appScope)
    }

    private fun enqueueImmediateAutoSync() {
        val request = OneTimeWorkRequestBuilder<AutoSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        // KEEP: if one is already queued or running, let it finish instead of piling up
        // duplicates for the same connectivity change - anything it doesn't catch (e.g. the
        // connection drops again mid-sync) gets picked up by the next reconnect or by the
        // periodic fallback below.
        WorkManager.getInstance(this)
            .enqueueUniqueWork("auto_sync_immediate", ExistingWorkPolicy.KEEP, request)
    }

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

        // Fallback net for the immediate connectivity-triggered sync above: catches the case
        // where connectivity returned while the process wasn't alive to observe the transition
        // (e.g. killed while offline, then relaunched by the system into the background).
        // NetworkType.CONNECTED means WorkManager itself defers this until a connection exists,
        // so it costs nothing while offline.
        val autoSyncRequest = PeriodicWorkRequestBuilder<AutoSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("auto_sync_periodic", ExistingPeriodicWorkPolicy.KEEP, autoSyncRequest)
    }
}
