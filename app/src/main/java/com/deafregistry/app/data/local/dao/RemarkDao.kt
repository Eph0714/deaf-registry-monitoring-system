package com.deafregistry.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.deafregistry.app.data.local.entity.RemarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RemarkDao {
    @Query("SELECT * FROM remarks WHERE visitUuid = :visitUuid AND isDeleted = 0 ORDER BY createdAt DESC")
    fun observeForVisit(visitUuid: String): Flow<List<RemarkEntity>>

    @Query("SELECT * FROM remarks WHERE isDirty = 1")
    suspend fun getDirty(): List<RemarkEntity>

    @Query("SELECT * FROM remarks WHERE uuid = :uuid")
    suspend fun getByUuid(uuid: String): RemarkEntity?

    @Query("DELETE FROM remarks WHERE uuid = :uuid")
    suspend fun hardDelete(uuid: String)

    // Deleting a visit deletes its remarks server-side too (ON DELETE CASCADE) - without this,
    // a remark still attached to a visit that's being hard-deleted locally (never synced) is
    // orphaned forever: its visitUuid no longer matches any local row, pushDirty() can never
    // find a parent for it, and it sits in "pending sync" permanently with no error shown.
    @Query("DELETE FROM remarks WHERE visitUuid = :visitUuid")
    suspend fun hardDeleteForVisit(visitUuid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: RemarkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<RemarkEntity>)

    // Reconciles a remark deleted on another device - upsertAll alone only ever adds/updates,
    // never removes. Mirrors VisitDao.clearSyncedExcept exactly.
    @Query("DELETE FROM remarks WHERE serverId IS NOT NULL AND uuid NOT IN (:protectedUuids)")
    suspend fun clearSyncedExcept(protectedUuids: List<String>)
}
