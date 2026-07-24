package com.deafregistry.app.data.repository

import com.deafregistry.app.data.remote.ApiService
import retrofit2.HttpException

class AdminRepository(private val api: ApiService) {
    suspend fun createServerBackup() = api.createBackup()
    suspend fun listServerBackups() = api.listBackups()

    /**
     * Super Admin only - wipes all registry/activity data, keeping reference data and the caller's own account.
     * Response<Unit> doesn't throw on a non-2xx by itself, so a rejected (e.g. 403) or failed request would
     * otherwise be silently treated as success by callers - throw explicitly so failures actually surface.
     */
    suspend fun resetAllData() {
        val response = api.resetAllData()
        if (!response.isSuccessful) throw HttpException(response)
    }
}
