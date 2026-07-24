package com.deafregistry.app.data.repository

import android.content.SharedPreferences
import com.deafregistry.app.data.remote.ApiService
import com.deafregistry.app.data.remote.dto.OverdueDaysDto

class SettingsRepository(
    private val api: ApiService,
    private val prefs: SharedPreferences
) {
    suspend fun refreshOverdueDays(): Int {
        val remote = api.getOverdueDays().overdueDays
        prefs.edit().putInt(KEY_OVERDUE_DAYS, remote).apply()
        return remote
    }

    suspend fun updateOverdueDays(days: Int): Int {
        val response = api.updateOverdueDays(OverdueDaysDto(days)).overdueDays
        prefs.edit().putInt(KEY_OVERDUE_DAYS, response).apply()
        return response
    }

    fun cachedOverdueDays(): Long = prefs.getInt(KEY_OVERDUE_DAYS, DEFAULT_OVERDUE_DAYS).toLong()

    companion object {
        private const val DEFAULT_OVERDUE_DAYS = 30
        private const val KEY_OVERDUE_DAYS = "overdue_days"
    }
}
