package com.deafregistry.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.deafregistry.app.di.ServiceLocator

/**
 * Background counterpart to the user tapping the Sync button: pushes locally-queued (dirty)
 * edits and pulls fresh data, without any UI interaction.
 *
 * Enqueued from two places (see DeafRegistryApp):
 *  - immediately when connectivity transitions from offline to online, so queued edits leave
 *    the device as soon as a signal is available;
 *  - on a periodic fallback schedule, in case the immediate trigger was missed (e.g. the
 *    process was killed while offline and connectivity returned while it wasn't running).
 * Both are gated on a NetworkType.CONNECTED constraint, so WorkManager itself won't even start
 * this while offline - no need to duplicate that check here.
 */
class AutoSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Not logged in yet (e.g. first launch, or after logging out) - nothing to push/pull,
        // and calling the API without a session would just fail every request.
        if (ServiceLocator.sessionManager.session.value == null) return Result.success()

        // SyncManager.sync() already best-effort catches per-step failures internally (a slow
        // reference-data call shouldn't block pushing dirty records, etc.), so a thrown
        // exception here means something more fundamental failed (e.g. pushDirty()/
        // refreshFromServer() itself, which aren't wrapped in runCatching). Retry rather than
        // silently dropping it - WorkManager backs off automatically.
        return runCatching { ServiceLocator.syncManager.sync() }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }
}
