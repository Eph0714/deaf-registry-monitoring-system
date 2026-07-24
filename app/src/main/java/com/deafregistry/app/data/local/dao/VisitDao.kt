package com.deafregistry.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.deafregistry.app.data.local.entity.VisitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitDao {
    @Query("SELECT * FROM visits WHERE deafIndividualUuid = :deafUuid ORDER BY visitDateTime DESC")
    fun observeForDeaf(deafUuid: String): Flow<List<VisitEntity>>

    @Query("SELECT * FROM visits WHERE isDirty = 1")
    suspend fun getDirty(): List<VisitEntity>

    @Query("SELECT * FROM visits WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): VisitEntity?

    @Query("SELECT * FROM visits ORDER BY visitDateTime DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<VisitEntity>>

    @Query(
        """
        SELECT MAX(visitDateTime) FROM visits WHERE deafIndividualUuid = :deafUuid
        """
    )
    suspend fun lastVisitDateTime(deafUuid: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: VisitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<VisitEntity>)
}
