package com.deafregistry.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.deafregistry.app.data.local.entity.VisitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitDao {
    @Query("SELECT * FROM visits WHERE deafIndividualUuid = :deafUuid AND isDeleted = 0 ORDER BY visitDateTime DESC")
    fun observeForDeaf(deafUuid: String): Flow<List<VisitEntity>>

    @Query("SELECT * FROM visits WHERE isDirty = 1")
    suspend fun getDirty(): List<VisitEntity>

    @Query("SELECT * FROM visits WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): VisitEntity?

    @Query("SELECT * FROM visits WHERE isDeleted = 0 ORDER BY visitDateTime DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<VisitEntity>>

    @Query(
        """
        SELECT MAX(visitDateTime) FROM visits WHERE deafIndividualUuid = :deafUuid AND isDeleted = 0
        """
    )
    suspend fun lastVisitDateTime(deafUuid: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: VisitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<VisitEntity>)

    @Query("DELETE FROM visits WHERE uuid = :uuid")
    suspend fun hardDelete(uuid: String)

    // Reconciles a visit deleted on another device - upsertAll alone only ever adds/updates, never
    // removes, so without this a visit deleted elsewhere would linger locally forever, "not
    // updating even after sync." Excludes dirty (not-yet-pushed) and never-synced rows via the
    // caller-supplied protected list, and only ever touches rows that came from the server in the
    // first place (serverId IS NOT NULL) - mirrors DeafIndividualDao.clearSyncedExcept exactly.
    @Query("DELETE FROM visits WHERE serverId IS NOT NULL AND uuid NOT IN (:protectedUuids)")
    suspend fun clearSyncedExcept(protectedUuids: List<String>)

    @Query("DELETE FROM visits WHERE deafIndividualUuid = :deafUuid AND serverId IS NOT NULL AND uuid NOT IN (:protectedUuids)")
    suspend fun clearSyncedExceptForDeaf(deafUuid: String, protectedUuids: List<String>)

    // Maps a visit's server id back to its local uuid FK - remarks are keyed locally by visitUuid
    // but the server only knows visit_id, same need ServerIdUuid already solves for individuals.
    @Query("SELECT serverId, uuid FROM visits WHERE serverId IS NOT NULL")
    suspend fun getServerIdUuidPairs(): List<ServerIdUuid>
}
