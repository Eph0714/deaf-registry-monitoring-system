package com.deafregistry.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.deafregistry.app.di.ServiceLocator

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!ServiceLocator.sessionManager.isLoggedIn()) return Result.success()
        return try {
            ServiceLocator.syncManager.sync()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
