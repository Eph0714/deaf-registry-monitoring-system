package com.deafregistry.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.deafregistry.app.data.local.entity.MunicipalityEntity
import kotlinx.coroutines.flow.Flow

data class MunicipalityWithCount(
    val id: Int,
    val name: String,
    val updatedAt: String,
    val deafCount: Int
)

@Dao
interface MunicipalityDao {
    @Query("SELECT * FROM municipalities ORDER BY name ASC")
    fun observeAll(): Flow<List<MunicipalityEntity>>

    @Query(
        """
        SELECT m.id as id, m.name as name, m.updatedAt as updatedAt, COUNT(d.uuid) as deafCount
        FROM municipalities m
        LEFT JOIN deaf_individuals d ON d.municipalityId = m.id AND d.isDeleted = 0
        GROUP BY m.id, m.name, m.updatedAt
        ORDER BY m.name ASC
        """
    )
    fun observeAllWithCounts(): Flow<List<MunicipalityWithCount>>

    @Query("SELECT * FROM municipalities ORDER BY name ASC")
    suspend fun getAll(): List<MunicipalityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<MunicipalityEntity>)

    @Query("DELETE FROM municipalities")
    suspend fun clear()
}
