package com.deafregistry.app.data.repository

import com.deafregistry.app.data.remote.ApiService

class ReportRepository(private val api: ApiService) {
    suspend fun summary() = api.reportSummary()
    suspend fun byMunicipality() = api.reportByMunicipality()
    suspend fun byMunicipalityStatus() = api.reportByMunicipalityStatus()
    suspend fun byBarangay() = api.reportByBarangay()
    suspend fun byGender() = api.reportByGender()
    suspend fun bySkill() = api.reportBySkill()
    suspend fun byStatus() = api.reportByStatus()
    suspend fun byConductor() = api.reportByConductor()
    suspend fun recentVisits(limit: Int = 20) = api.reportRecentVisits(limit)
    suspend fun notVisited(days: Int = 30) = api.reportNotVisited(days)
}
