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

    suspend fun editVisit(uuid: String, visitDateTime: String, conductorName: String?) {
        val existing = visitDao.getByUuid(uuid) ?: return
        visitDao.upsert(existing.copy(visitDateTime = visitDateTime, conductorName = conductorName, isDirty = true))
    }

    suspend fun deleteVisit(uuid: String) {
        val existing = visitDao.getByUuid(uuid) ?: return
        if (existing.serverId == null) {
            visitDao.hardDelete(uuid)
        } else {
            visitDao.upsert(existing.copy(isDeleted = true, isDirty = true))
        }
    }

    suspend fun refreshForDeaf(deafUuid: String, deafServerId: Int) {
        val remote = api.getVisits(deafServerId)
        val dirtyUuids = visitDao.getDirty().map { it.uuid }.toSet()
        val entities = remote.filter { it.uuid !in dirtyUuids }.map { toEntity(it, deafUuid) }
        visitDao.upsertAll(entities)
        val protectedUuids = entities.map { it.uuid } + dirtyUuids
        visitDao.clearSyncedExceptForDeaf(deafUuid, protectedUuids)
    }

    /**
     * Pulls every visit across the whole roster in one call, not just whichever individuals this
     * device happens to have opened the profile of - refreshForDeaf() alone meant a visit recorded
     * on another device only ever appeared locally once someone opened that exact individual's
     * profile screen, so anything relying on the local `visits` table roster-wide (the "Last Visit"
     * grouping/sort in All Deaf Records, per-row last-visited display, etc.) silently stayed stale
     * on every other device. Called from SyncManager.pull()/sync() after the deaf-individuals
     * refresh, since it needs local serverId->uuid mappings to already be up to date.
     */
    suspend fun refreshAll() {
        val remote = api.getAllVisits()
        val uuidByServerId = deafDao.getServerIdUuidPairs().associate { it.serverId to it.uuid }
        val dirtyUuids = visitDao.getDirty().map { it.uuid }.toSet()
        val entities = remote.mapNotNull { dto ->
            if (dto.uuid in dirtyUuids) return@mapNotNull null
            val deafUuid = uuidByServerId[dto.deafIndividualId] ?: return@mapNotNull null
            toEntity(dto, deafUuid)
        }
        visitDao.upsertAll(entities)
        // Reconciles a visit edited or deleted on another device - upsertAll only ever adds/updates,
        // so without this, a visit deleted elsewhere (or an edit that landed on a device whose local
        // uuid mapping couldn't be resolved above) would keep showing stale locally forever, "not
        // updating even after sync." Protects both what was just upserted and anything still dirty
        // (not yet pushed) here on this device - only ever touches previously-synced rows.
        val protectedUuids = entities.map { it.uuid } + dirtyUuids
        visitDao.clearSyncedExcept(protectedUuids)
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
                if (item.isDeleted) {
                    item.serverId?.let {
                        // Response<Unit> doesn't throw on a non-2xx by itself - without this check,
                        // a failed server-side delete would still get hard-deleted locally.
                        val response = api.deleteVisit(it)
                        if (!response.isSuccessful) throw retrofit2.HttpException(response)
                    }
                    visitDao.hardDelete(item.uuid)
                    continue
                }
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
                if (item.serverId == null) {
                    val response = api.createVisit(deafServerId, request)
                    visitDao.upsert(item.copy(serverId = response.id, isDirty = false))
                } else {
                    val response = api.updateVisit(item.serverId, request)
                    visitDao.upsert(item.copy(visitDateTime = response.visitDateTime, isDirty = false))
                }
            } catch (e: Exception) {
                // retry next sync
            }
        }
    }
}
