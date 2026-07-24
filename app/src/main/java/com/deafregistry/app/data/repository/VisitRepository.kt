package com.deafregistry.app.data.repository

import com.deafregistry.app.data.local.dao.DeafIndividualDao
import com.deafregistry.app.data.local.dao.VisitDao
import com.deafregistry.app.data.local.entity.VisitEntity
import com.deafregistry.app.data.remote.ApiService
import com.deafregistry.app.data.remote.dto.VisitDto
import com.deafregistry.app.data.remote.dto.VisitRequest
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

class VisitRepository(
    private val api: ApiService,
    private val visitDao: VisitDao,
    private val deafDao: DeafIndividualDao
) {
    fun observeForDeaf(deafUuid: String): Flow<List<VisitEntity>> = visitDao.observeForDeaf(deafUuid)
    fun observeRecent(limit: Int = 20): Flow<List<VisitEntity>> = visitDao.observeRecent(limit)

    suspend fun lastVisitDateTime(deafUuid: String): String? = visitDao.lastVisitDateTime(deafUuid)

    suspend fun dirtyCount(): Int = visitDao.getDirty().size

    suspend fun recordVisit(
        deafUuid: String,
        latitude: Double?,
        longitude: Double?,
        conductorId: Int?,
        conductorName: String?,
        visitDateTime: String? = null
    ): String {
        val uuid = UUID.randomUUID().toString()
        val dateTime = visitDateTime ?: DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(java.time.LocalDateTime.now())
        val entity = VisitEntity(
            uuid = uuid,
            serverId = null,
            deafIndividualUuid = deafUuid,
            visitDateTime = dateTime,
            latitude = latitude,
            longitude = longitude,
            conductorId = conductorId,
            conductorName = conductorName,
            updatedAt = Instant.now().toString(),
            isDirty = true
        )
        visitDao.upsert(entity)
        return uuid
    }

    suspend fun refreshForDeaf(deafUuid: String, deafServerId: Int) {
        val remote = api.getVisits(deafServerId)
        val dirtyUuids = visitDao.getDirty().map { it.uuid }.toSet()
        val entities = remote.filter { it.uuid !in dirtyUuids }.map { toEntity(it, deafUuid) }
        visitDao.upsertAll(entities)
    }

    private fun toEntity(dto: VisitDto, deafUuid: String) = VisitEntity(
        uuid = dto.uuid,
        serverId = dto.id,
        deafIndividualUuid = deafUuid,
        visitDateTime = dto.visitDateTime,
        latitude = dto.latitude,
        longitude = dto.longitude,
        conductorId = dto.conductorId,
        conductorName = dto.conductorName ?: dto.conductorTeacherName,
        updatedAt = dto.updatedAt,
        isDirty = false
    )

    suspend fun pushDirty() {
        for (item in visitDao.getDirty()) {
            try {
                val deaf = deafDao.getById(item.deafIndividualUuid) ?: continue
                val deafServerId = deaf.serverId ?: continue // parent must be synced first
                val request = VisitRequest(
                    uuid = item.uuid,
                    latitude = item.latitude,
                    longitude = item.longitude,
                    conductor_id = item.conductorId,
                    conductor_name = item.conductorName,
                    visit_datetime = item.visitDateTime
                )
                val response = api.createVisit(deafServerId, request)
                visitDao.upsert(item.copy(serverId = response.id, isDirty = false))
            } catch (e: Exception) {
                // retry next sync
            }
        }
    }
}
