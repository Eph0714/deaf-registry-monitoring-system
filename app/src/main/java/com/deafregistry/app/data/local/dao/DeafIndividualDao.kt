package com.deafregistry.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.deafregistry.app.data.local.entity.DeafIndividualEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeafIndividualDao {

    @Query(
        """
        SELECT * FROM deaf_individuals
        WHERE isDeleted = 0
          AND municipalityId = :municipalityId
          AND (:barangayId IS NULL OR barangayId = :barangayId)
          AND (:skillLevel IS NULL OR skillLevel = :skillLevel)
          AND (:monitoringStatus IS NULL OR monitoringStatus = :monitoringStatus)
          AND (:gender IS NULL OR gender = :gender)
          AND (:query IS NULL OR fullName LIKE '%' || :query || '%')
        ORDER BY fullName ASC
        """
    )
    fun observeForMunicipality(
        municipalityId: Int,
        barangayId: Int?,
        skillLevel: String?,
        monitoringStatus: String?,
        gender: String?,
        query: String?
    ): Flow<List<DeafIndividualEntity>>

    @Query(
        """
        SELECT * FROM deaf_individuals
        WHERE isDeleted = 0 AND (
          fullName LIKE '%' || :query || '%' OR
          municipalityName LIKE '%' || :query || '%' OR
          barangayName LIKE '%' || :query || '%' OR
          assignedTeacherName LIKE '%' || :query || '%' OR
          skillLevel LIKE '%' || :query || '%' OR
          monitoringStatus LIKE '%' || :query || '%'
        )
        ORDER BY fullName ASC
        """
    )
    fun search(query: String): Flow<List<DeafIndividualEntity>>

    @Query("SELECT * FROM deaf_individuals WHERE uuid = :uuid LIMIT 1")
    fun observeById(uuid: String): Flow<DeafIndividualEntity?>

    @Query("SELECT * FROM deaf_individuals WHERE uuid = :uuid LIMIT 1")
    suspend fun getById(uuid: String): DeafIndividualEntity?

    @Query("SELECT * FROM deaf_individuals WHERE isDeleted = 0")
    suspend fun getAllActive(): List<DeafIndividualEntity>

    @Query("SELECT * FROM deaf_individuals WHERE isDeleted = 0 ORDER BY fullName ASC")
    fun observeAllActive(): Flow<List<DeafIndividualEntity>>

    @Query("SELECT * FROM deaf_individuals WHERE isDeleted = 0 ORDER BY assignedTeacherName IS NULL, assignedTeacherName ASC, fullName ASC")
    fun observeAllActiveByConductor(): Flow<List<DeafIndividualEntity>>

    // Ascending age = youngest first = most recent birth_date first, so this orders birthDate
    // descending; records with no birth_date recorded sort last rather than first.
    @Query("SELECT * FROM deaf_individuals WHERE isDeleted = 0 ORDER BY birthDate IS NULL, birthDate DESC")
    fun observeAllActiveByAge(): Flow<List<DeafIndividualEntity>>

    @Query(
        """
        SELECT d.* FROM deaf_individuals d
        LEFT JOIN (SELECT deafIndividualUuid, MAX(visitDateTime) AS lastVisit FROM visits GROUP BY deafIndividualUuid) v
        ON v.deafIndividualUuid = d.uuid
        WHERE d.isDeleted = 0
        ORDER BY v.lastVisit IS NULL, v.lastVisit DESC
        """
    )
    fun observeAllActiveByLastVisit(): Flow<List<DeafIndividualEntity>>

    /**
     * Backs the Reports drill-down (tap a category value like "BS" or a municipality to see
     * its individual records). Exactly one of these filters is non-null per call, except
     * barangayName which is always paired with municipalityName since barangay names aren't
     * unique across municipalities.
     */
    @Query(
        """
        SELECT * FROM deaf_individuals
        WHERE isDeleted = 0
          AND (:municipalityName IS NULL OR municipalityName = :municipalityName)
          AND (:barangayName IS NULL OR barangayName = :barangayName)
          AND (:gender IS NULL OR gender = :gender)
          AND (:skillLevel IS NULL OR skillLevel = :skillLevel)
          AND (:monitoringStatus IS NULL OR monitoringStatus = :monitoringStatus)
          AND (:teacherName IS NULL OR assignedTeacherName = :teacherName)
        ORDER BY fullName ASC
        """
    )
    fun observeByCategory(
        municipalityName: String? = null,
        barangayName: String? = null,
        gender: String? = null,
        skillLevel: String? = null,
        monitoringStatus: String? = null,
        teacherName: String? = null
    ): Flow<List<DeafIndividualEntity>>

    @Query("SELECT * FROM deaf_individuals WHERE isDirty = 1 OR photoDirty = 1")
    suspend fun getDirty(): List<DeafIndividualEntity>

    @Query("SELECT COUNT(*) FROM deaf_individuals WHERE isDeleted = 0 AND municipalityId = :municipalityId")
    fun observeCountForMunicipality(municipalityId: Int): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DeafIndividualEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<DeafIndividualEntity>)

    @Query("DELETE FROM deaf_individuals WHERE uuid = :uuid")
    suspend fun hardDelete(uuid: String)

    @Query("DELETE FROM deaf_individuals WHERE serverId IS NOT NULL")
    suspend fun clearSynced()
}
