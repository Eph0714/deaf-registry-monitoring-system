package com.deafregistry.app.data.repository

import com.deafregistry.app.data.remote.ApiService

class AdminRepository(private val api: ApiService) {
    suspend fun createServerBackup() = api.createBackup()
    suspend fun listServerBackups() = api.listBackups()

    /** Super Admin only - wipes all registry/activity data, keeping reference data and the caller's own account. */
    suspend fun resetAllData() = api.resetAllData()
}
