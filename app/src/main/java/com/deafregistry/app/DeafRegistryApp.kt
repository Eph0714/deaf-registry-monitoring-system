package com.deafregistry.app

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.deafregistry.app.data.sync.SyncWorker
import com.deafregistry.app.data.sync.VisitDueWorker
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.util.NotificationHelper
import java.util.concurrent.TimeUnit
import androidx.work.Constraints

class DeafRegistryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        NotificationHelper.ensureChannel(this)
        schedulePeriodicWork()
        observeConnectivityForImmediateSync()
    }

    private fun schedulePeriodicWork() {
        val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(networkConstraints)
            .build()

        val visitDueRequest = PeriodicWorkRequestBuilder<VisitDueWorker>(12, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(this).apply {
            enqueueUniquePeriodicWork("sync_work", ExistingPeriodicWorkPolicy.KEEP, syncRequest)
            enqueueUniquePeriodicWork("visit_due_work", ExistingPeriodicWorkPolicy.KEEP, visitDueRequest)
        }
    }

    /**
     * The periodic sync worker only runs on its ~30-minute schedule. This listens for the
     * device regaining connectivity and fires an immediate one-time sync instead of waiting
     * for the next periodic window, so locally-queued (dirty) records push out as soon as
     * the device is back online.
     */
    private fun observeConnectivityForImmediateSync() {
        val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val immediateSyncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
                WorkManager.getInstance(this@DeafRegistryApp)
                    .enqueueUniqueWork("immediate_sync_on_reconnect", ExistingWorkPolicy.KEEP, immediateSyncRequest)
            }
        })
    }
}
