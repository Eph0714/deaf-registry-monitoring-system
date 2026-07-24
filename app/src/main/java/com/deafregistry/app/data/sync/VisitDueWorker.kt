package com.deafregistry.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.util.NotificationHelper
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class VisitDueWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val session = ServiceLocator.sessionManager.session.value ?: return Result.success()

        val deafDao = ServiceLocator.database.deafIndividualDao()
        val visitDao = ServiceLocator.database.visitDao()
        val overdueDays = ServiceLocator.settingsRepository.cachedOverdueDays()
        val now = LocalDateTime.now()

        val candidates = deafDao.getAllActive().filter { individual ->
            if (session.role != "admin" && individual.assignedTeacherId != session.teacherId) return@filter false
            individual.monitoringStatus in listOf("RV", "BS")
        }

        val overdue = candidates.filter { individual ->
            val last = visitDao.lastVisitDateTime(individual.uuid) ?: return@filter true
            val lastDateTime = runCatching { LocalDateTime.parse(last, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }.getOrNull()
                ?: return@filter true
            ChronoUnit.DAYS.between(lastDateTime, now) >= overdueDays
        }

        if (overdue.isNotEmpty()) {
            val maxNames = 5
            val names = overdue.take(maxNames).joinToString(", ") { it.fullName }
            val remainder = overdue.size - maxNames
            val detail = if (remainder > 0) "$names, and $remainder more" else names
            NotificationHelper.notify(
                applicationContext,
                id = 1001,
                title = "${overdue.size} visit(s) overdue",
                text = "Not visited in the last $overdueDays+ days: $detail"
            )
        }

        return Result.success()
    }
}
