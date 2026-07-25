package com.deafregistry.app.data.repository

import com.deafregistry.app.data.local.dao.RemarkDao
import com.deafregistry.app.data.local.dao.VisitDao
import com.deafregistry.app.data.local.entity.RemarkEntity
import com.deafregistry.app.data.remote.ApiService
import com.deafregistry.app.data.remote.dto.RemarkDto
import com.deafregistry.app.data.remote.dto.RemarkRequest
import com.deafregistry.app.data.remote.dto.RemarkUpdateRequest
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

class RemarkRepository(
    private val api: ApiService,
    private val remarkDao: RemarkDao,
    private val visitDao: VisitDao
) {
    fun observeForVisit(visitUuid: String): Flow<List<RemarkEntity>> = remarkDao.observeForVisit(visitUuid)

    suspend fun dirtyCount(): Int = remarkDao.getDirty().size

    suspend fun addRemark(visitUuid: String, userId: Int?, userName: String, text: String): String {
        val uuid = UUID.randomUUID().toString()
        val entity = RemarkEntity(
            uuid = uuid,
            serverId = null,
            visitUuid = visitUuid,
            userId = userId,
            userName = userName,
            remarkText = text,
            createdAt = Instant.now().toString(),
            isDirty = true
        )
        remarkDao.upsert(entity)
        return uuid
    }

    suspend fun editRemark(uuid: String, newText: String) {
        val existing = remarkDao.getByUuid(uuid) ?: return
        remarkDao.upsert(existing.copy(remarkText = newText, isDirty = true))
    }

    suspend fun deleteRemark(uuid: String) {
        val existing = remarkDao.getByUuid(uuid) ?: return
        if (existing.serverId == null) {
            remarkDao.hardDelete(uuid)
        } else {
            remarkDao.upsert(existing.copy(isDeleted = true, isDirty = true))
        }
    }

    suspend fun refreshForVisit(visitUuid: String, visitServerId: Int) {
        val remote = api.getRemarks(visitServerId)
        val dirtyUuids = remarkDao.getDirty().map { it.uuid }.toSet()
        val entities = remote.filter { it.uuid !in dirtyUuids }.map { toEntity(it, visitUuid) }
        remarkDao.upsertAll(entities)
    }

    private fun toEntity(dto: RemarkDto, visitUuid: String) = RemarkEntity(
        uuid = dto.uuid,
        serverId = dto.id,
        visitUuid = visitUuid,
        userId = dto.userId,
        userName = dto.userName,
        remarkText = dto.remarkText,
        createdAt = dto.createdAt,
        isDirty = false
    )

    suspend fun pushDirty() {
        for (item in remarkDao.getDirty()) {
            try {
                val visit = visitDao.getByUuid(item.visitUuid) ?: continue
                val visitServerId = visit.serverId ?: continue // parent visit must be synced first
                if (item.isDeleted) {
                    item.serverId?.let {
                        // Response<Unit> doesn't throw on a non-2xx by itself - without this check,
                        // a failed server-side delete would still get hard-deleted locally.
                        val response = api.deleteRemark(visitServerId, it)
                        if (!response.isSuccessful) throw retrofit2.HttpException(response)
                    }
                    remarkDao.hardDelete(item.uuid)
                    continue
                }
                if (item.serverId == null) {
                    val response = api.createRemark(visitServerId, RemarkRequest(remark_text = item.remarkText, uuid = item.uuid))
                    remarkDao.upsert(item.copy(serverId = response.id, isDirty = false))
                } else {
                    val response = api.updateRemark(visitServerId, item.serverId, RemarkUpdateRequest(item.remarkText))
                    if (!response.isSuccessful) throw retrofit2.HttpException(response)
                    remarkDao.upsert(item.copy(isDirty = false))
                }
            } catch (e: Exception) {
                // retry next sync
            }
        }
    }
}
