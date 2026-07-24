package com.deafregistry.app.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.deafregistry.app.data.local.entity.DeafIndividualEntity
import kotlinx.coroutines.flow.Flow

data class MunicipalityVisitReportRow(
    val municipality: String,
    val fullName: String,
    val status: String,
    val lastVisitDate: String?,
    val lastVisitedBy: String?
)

data class DeafIndividualWithLastVisit(
    @Embedded val individual: DeafIndividualEntity,
    val lastVisitDate: String?
)

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

    @Query("SELECT * FROM deaf_individuals WHERE isDeleted = 0 ORDER BY municipalityName ASC, fullName ASC")
    fun observeAllActiveByMunicipality(): Flow<List<DeafIndividualEntity>>

    @Query("SELECT * FROM deaf_individuals WHERE isDeleted = 0 ORDER BY municipalityName ASC, barangayName ASC, fullName ASC")
    fun observeAllActiveByBarangay(): Flow<List<DeafIndividualEntity>>

    /**
     * Carries each individual's most recent visit date alongside them, for grouping by day.
     * Ordered by the *date portion* of the last visit (descending) then name (ascending) -
     * not by the exact timestamp - so two people visited on the same day but at different
     * times still land in one group, sorted by name within it.
     */
    @Query(
        """
        SELECT d.*, lv.lastVisit AS lastVisitDate
        FROM deaf_individuals d
        LEFT JOIN (
            SELECT v.deafIndividualUuid, v.visitDateTime AS lastVisit
            FROM visits v
            WHERE v.visitDateTime = (
                SELECT MAX(v2.visitDateTime) FROM visits v2 WHERE v2.deafIndividualUuid = v.deafIndividualUuid
            )
        ) lv ON lv.deafIndividualUuid = d.uuid
        WHERE d.isDeleted = 0
        GROUP BY d.uuid
        ORDER BY lv.lastVisit IS NULL, SUBSTR(lv.lastVisit, 1, 10) DESC, d.fullName ASC
        """
    )
    fun observeAllActiveWithLastVisit(): Flow<List<DeafIndividualWithLastVisit>>

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

    /**
     * Backs the Reports export: every active individual grouped/ordered by municipality
     * ascending then name ascending, each with their most recent visit date and who conducted
     * it (both null if never visited).
     */
    @Query(
        """
        SELECT d.municipalityName AS municipality, d.fullName AS fullName, d.monitoringStatus AS status,
               lv.lastVisit AS lastVisitDate, lv.conductorName AS lastVisitedBy
        FROM deaf_individuals d
        LEFT JOIN (
            SELECT v.deafIndividualUuid, v.visitDateTime AS lastVisit, v.conductorName
            FROM visits v
            WHERE v.visitDateTime = (
                SELECT MAX(v2.visitDateTime) FROM visits v2 WHERE v2.deafIndividualUuid = v.deafIndividualUuid
            )
        ) lv ON lv.deafIndividualUuid = d.uuid
        WHERE d.isDeleted = 0
        GROUP BY d.uuid
        ORDER BY d.municipalityName ASC, d.fullName ASC
        """
    )
    suspend fun getMunicipalityVisitReport(): List<MunicipalityVisitReportRow>

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
