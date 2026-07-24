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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: RemarkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<RemarkEntity>)
}
