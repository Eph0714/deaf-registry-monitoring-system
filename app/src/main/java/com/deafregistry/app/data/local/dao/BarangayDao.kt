package com.deafregistry.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.deafregistry.app.data.local.entity.BarangayEntity
import kotlinx.coroutines.flow.Flow

data class BarangayWithCount(
    val id: Int,
    val name: String,
    val municipalityId: Int,
    val municipalityName: String,
    val deafCount: Int
)

@Dao
interface BarangayDao {
    @Query("SELECT * FROM barangays WHERE municipalityId = :municipalityId ORDER BY name ASC")
    fun observeForMunicipality(municipalityId: Int): Flow<List<BarangayEntity>>

    @Query(
        """
        SELECT b.id as id, b.name as name, b.municipalityId as municipalityId, b.municipalityName as municipalityName, COUNT(d.uuid) as deafCount
        FROM barangays b
        LEFT JOIN deaf_individuals d ON d.barangayId = b.id AND d.isDeleted = 0
        WHERE b.municipalityId = :municipalityId
        GROUP BY b.id, b.name, b.municipalityId, b.municipalityName
        ORDER BY b.name ASC
        """
    )
    fun observeForMunicipalityWithCounts(municipalityId: Int): Flow<List<BarangayWithCount>>

    @Query(
        """
        SELECT b.id as id, b.name as name, b.municipalityId as municipalityId, b.municipalityName as municipalityName, COUNT(d.uuid) as deafCount
        FROM barangays b
        LEFT JOIN deaf_individuals d ON d.barangayId = b.id AND d.isDeleted = 0
        GROUP BY b.id, b.name, b.municipalityId, b.municipalityName
        ORDER BY b.municipalityName ASC, b.name ASC
        """
    )
    fun observeAllWithCounts(): Flow<List<BarangayWithCount>>

    @Query("SELECT * FROM barangays ORDER BY name ASC")
    suspend fun getAll(): List<BarangayEntity>

    @Query("SELECT * FROM barangays WHERE municipalityId = :municipalityId ORDER BY name ASC")
    suspend fun getForMunicipality(municipalityId: Int): List<BarangayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<BarangayEntity>)

    @Query("DELETE FROM barangays")
    suspend fun clear()
}
