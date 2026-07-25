package com.deafregistry.app.data.repository

import android.content.Context
import com.deafregistry.app.data.local.dao.BarangayDao
import com.deafregistry.app.data.local.dao.DeafIndividualDao
import com.deafregistry.app.data.local.dao.DeafIndividualWithLastVisit
import com.deafregistry.app.data.local.dao.MunicipalityDao
import com.deafregistry.app.data.local.dao.TeacherDao
import com.deafregistry.app.data.local.entity.DeafIndividualEntity
import com.deafregistry.app.data.remote.ApiService
import com.deafregistry.app.data.remote.dto.DeafIndividualDto
import com.deafregistry.app.data.remote.dto.DeafIndividualRequest
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.time.Instant
import java.util.UUID

class DeafIndividualRepository(
    private val context: Context,
    private val api: ApiService,
    private val dao: DeafIndividualDao,
    private val municipalityDao: MunicipalityDao,
    private val barangayDao: BarangayDao,
    private val teacherDao: TeacherDao
) {
    fun observeForMunicipality(
        municipalityId: Int,
        barangayId: Int? = null,
        skillLevel: String? = null,
        monitoringStatus: String? = null,
        gender: String? = null,
        query: String? = null
    ): Flow<List<DeafIndividualEntity>> = dao.observeForMunicipality(
        municipalityId, barangayId, skillLevel, monitoringStatus, gender, query?.takeIf { it.isNotBlank() }
    )

    fun observeById(uuid: String): Flow<DeafIndividualEntity?> = dao.observeById(uuid)

    fun observeAllActive(): Flow<List<DeafIndividualEntity>> = dao.observeAllActive()

    fun observeAllActiveByConductor(): Flow<List<DeafIndividualEntity>> = dao.observeAllActiveByConductor()

    fun observeAllActiveByMunicipality(): Flow<List<DeafIndividualEntity>> = dao.observeAllActiveByMunicipality()

    fun observeAllActiveByBarangay(): Flow<List<DeafIndividualEntity>> = dao.observeAllActiveByBarangay()

    fun observeAllActiveByStatus(): Flow<List<DeafIndividualEntity>> = dao.observeAllActiveByStatus()

    fun observeAllActiveWithLastVisit(): Flow<List<DeafIndividualWithLastVisit>> = dao.observeAllActiveWithLastVisit()

    fun observeByCategory(
        municipalityName: String? = null,
        barangayName: String? = null,
        gender: String? = null,
        skillLevel: String? = null,
        monitoringStatus: String? = null,
        teacherName: String? = null
    ): Flow<List<DeafIndividualEntity>> = dao.observeByCategory(
        municipalityName, barangayName, gender, skillLevel, monitoringStatus, teacherName
    )

    fun search(query: String): Flow<List<DeafIndividualEntity>> = dao.search(query)

    fun observeCountForMunicipality(municipalityId: Int): Flow<Int> = dao.observeCountForMunicipality(municipalityId)

    suspend fun getById(uuid: String): DeafIndividualEntity? = dao.getById(uuid)

    suspend fun dirtyCount(): Int = dao.getDirty().size

    suspend fun getMunicipalityVisitReport() = dao.getMunicipalityVisitReport()

    private fun toDto(dto: DeafIndividualDto): DeafIndividualEntity = DeafIndividualEntity(
        uuid = dto.uuid,
        serverId = dto.id,
        fullName = dto.fullName,
        photoUrl = dto.photoUrl,
        localPhotoPath = null,
        birthDate = dto.birthDate,
        gender = dto.gender,
        barangayId = dto.barangayId,
        barangayName = dto.barangayName,
        purok = dto.purok,
        municipalityId = dto.municipalityId,
        municipalityName = dto.municipalityName,
        latitude = dto.latitude,
        longitude = dto.longitude,
        skillLevel = dto.skillLevel,
        monitoringStatus = dto.monitoringStatus,
        assignedTeacherId = dto.assignedTeacherId,
        assignedTeacherName = dto.teacherName,
        assignedTeacherContact = dto.teacherContact,
        assignedDate = dto.assignedDate,
        contactNumber = dto.contactNumber,
        email = dto.email,
        maritalStatus = dto.maritalStatus,
        emergencyContactName = dto.emergencyContactName,
        emergencyContactNumber = dto.emergencyContactNumber,
        notes = dto.notes,
        updatedAt = dto.updatedAt,
        isDirty = false,
        isDeleted = false
    )

    suspend fun refreshFromServer() {
        val remote = api.getDeafIndividuals()
        val dirtyUuids = dao.getDirty().map { it.uuid }.toSet()
        val entities = remote.filter { it.uuid !in dirtyUuids }.map { toDto(it) }
        try {
            dao.upsertAll(entities)
        } catch (e: Exception) {
            // upsertAll is one transaction - a single malformed row (e.g. a legacy record with an
            // unexpectedly null field) would otherwise throw and silently block every other
            // record in the batch from syncing too. Fall back to one-at-a-time so a bad row only
            // costs itself, not everyone else's sync.
            android.util.Log.w("DeafIndividualRepository", "Batch upsert failed, retrying individually", e)
            for (entity in entities) {
                runCatching { dao.upsert(entity) }
                    .onFailure { android.util.Log.w("DeafIndividualRepository", "Skipping unsyncable record ${entity.uuid}", it) }
            }
        }
    }

    suspend fun createLocal(
        fullName: String,
        birthDate: String?,
        gender: String,
        barangayId: Int,
        purok: String?,
        municipalityId: Int,
        latitude: Double?,
        longitude: Double?,
        skillLevel: String,
        monitoringStatus: String,
        assignedTeacherId: Int?,
        assignedDate: String?,
        contactNumber: String?,
        email: String?,
        maritalStatus: String?,
        emergencyContactName: String?,
        emergencyContactNumber: String?,
        notes: String?
    ): String {
        val uuid = UUID.randomUUID().toString()
        val barangay = barangayDao.getForMunicipality(municipalityId).find { it.id == barangayId }
        val municipality = municipalityDao.getAll().find { it.id == municipalityId }
        val teacher = assignedTeacherId?.let { teacherDao.getById(it) }

        val entity = DeafIndividualEntity(
            uuid = uuid,
            serverId = null,
            fullName = fullName,
            photoUrl = null,
            localPhotoPath = null,
            birthDate = birthDate,
            gender = gender,
            barangayId = barangayId,
            barangayName = barangay?.name ?: "",
            purok = purok,
            municipalityId = municipalityId,
            municipalityName = municipality?.name ?: "",
            latitude = latitude,
            longitude = longitude,
            skillLevel = skillLevel,
            monitoringStatus = monitoringStatus,
            assignedTeacherId = assignedTeacherId,
            assignedTeacherName = teacher?.name,
            assignedTeacherContact = teacher?.contactNumber,
            assignedDate = assignedDate,
            contactNumber = contactNumber,
            email = email,
            maritalStatus = maritalStatus,
            emergencyContactName = emergencyContactName,
            emergencyContactNumber = emergencyContactNumber,
            notes = notes,
            updatedAt = Instant.now().toString(),
            isDirty = true,
            isDeleted = false
        )
        dao.upsert(entity)
        return uuid
    }

    suspend fun updateLocal(
        uuid: String,
        fullName: String,
        birthDate: String?,
        gender: String,
        barangayId: Int,
        purok: String?,
        municipalityId: Int,
        latitude: Double?,
        longitude: Double?,
        skillLevel: String,
        monitoringStatus: String,
        assignedTeacherId: Int?,
        assignedDate: String?,
        contactNumber: String?,
        email: String?,
        maritalStatus: String?,
        emergencyContactName: String?,
        emergencyContactNumber: String?,
        notes: String?
    ) {
        val existing = dao.getById(uuid) ?: return
        val barangay = barangayDao.getForMunicipality(municipalityId).find { it.id == barangayId }
        val municipality = municipalityDao.getAll().find { it.id == municipalityId }
        val teacher = assignedTeacherId?.let { teacherDao.getById(it) }

        dao.upsert(
            existing.copy(
                fullName = fullName,
                birthDate = birthDate,
                gender = gender,
                barangayId = barangayId,
                barangayName = barangay?.name ?: existing.barangayName,
                purok = purok,
                municipalityId = municipalityId,
                municipalityName = municipality?.name ?: existing.municipalityName,
                latitude = latitude,
                longitude = longitude,
                skillLevel = skillLevel,
                monitoringStatus = monitoringStatus,
                assignedTeacherId = assignedTeacherId,
                assignedTeacherName = teacher?.name,
                assignedTeacherContact = teacher?.contactNumber,
                assignedDate = assignedDate,
                contactNumber = contactNumber,
                email = email,
                maritalStatus = maritalStatus,
                emergencyContactName = emergencyContactName,
                emergencyContactNumber = emergencyContactNumber,
                notes = notes,
                updatedAt = Instant.now().toString(),
                isDirty = true
            )
        )
    }

    suspend fun setLocalPhoto(uuid: String, localPath: String) {
        val existing = dao.getById(uuid) ?: return
        dao.upsert(existing.copy(localPhotoPath = localPath, photoDirty = true, isDirty = true))
    }

    suspend fun deleteLocal(uuid: String) {
        val existing = dao.getById(uuid) ?: return
        if (existing.serverId == null) {
            dao.hardDelete(uuid)
        } else {
            dao.upsert(existing.copy(isDeleted = true, isDirty = true))
        }
    }

    /**
     * Pushes all locally-dirty records to the server. Best-effort; failures are skipped and
     * retried next sync.
     *
     * The base record and its photo are persisted as two separate steps (not one atomic
     * commit), because create()/update() and the photo upload can fail independently - e.g.
     * a slow/failed photo upload used to leave the whole record's serverId unsaved locally,
     * so the *next* sync attempt would call create() again with the same uuid and get a 409
     * Duplicate entry from the server (unique constraint on uuid), which this loop's blanket
     * catch swallowed silently - the record then stayed "dirty" forever with no visible error.
     * Persisting isDirty=false right after the base create/update succeeds means a retry only
     * ever repeats the (idempotent) photo upload, never re-creates the record.
     */
    suspend fun pushDirty() {
        for (item in dao.getDirty()) {
            try {
                if (item.isDeleted) {
                    item.serverId?.let {
                        val response = api.deleteDeafIndividual(it)
                        // Same Response<Unit>-doesn't-throw issue as the update path below - without
                        // this check, a failed server-side delete would still get hard-deleted locally,
                        // and the "deleted" record would silently reappear on the next pull since the
                        // server still has it and it's no longer excluded by the dirty filter.
                        if (!response.isSuccessful) throw retrofit2.HttpException(response)
                    }
                    dao.hardDelete(item.uuid)
                    continue
                }

                var current = item

                if (current.isDirty) {
                    val request = DeafIndividualRequest(
                        uuid = current.uuid,
                        full_name = current.fullName,
                        birth_date = current.birthDate,
                        gender = current.gender,
                        barangay_id = current.barangayId,
                        purok = current.purok,
                        municipality_id = current.municipalityId,
                        latitude = current.latitude,
                        longitude = current.longitude,
                        skill_level = current.skillLevel,
                        monitoring_status = current.monitoringStatus,
                        assigned_teacher_id = current.assignedTeacherId,
                        assigned_date = current.assignedDate,
                        contact_number = current.contactNumber,
                        email = current.email,
                        marital_status = current.maritalStatus,
                        emergency_contact_name = current.emergencyContactName,
                        emergency_contact_number = current.emergencyContactNumber,
                        notes = current.notes
                    )

                    val serverId = if (current.serverId == null) {
                        try {
                            api.createDeafIndividual(request).id
                        } catch (e: retrofit2.HttpException) {
                            // A 409 here means a record with this uuid already exists server-side -
                            // most likely from an earlier sync attempt that created it successfully
                            // but never got to persist that locally (e.g. the photo step failed
                            // right after, before this bug fix). Recover by finding it and updating
                            // it instead of leaving this record stuck forever.
                            if (e.code() == 409) {
                                val existing = api.getDeafIndividuals().find { it.uuid == current.uuid } ?: throw e
                                val response = api.updateDeafIndividual(existing.id, request)
                                if (!response.isSuccessful) throw retrofit2.HttpException(response)
                                existing.id
                            } else throw e
                        }
                    } else {
                        val response = api.updateDeafIndividual(current.serverId, request)
                        // Response<Unit> doesn't throw on a non-2xx by itself - without this check,
                        // a failed edit would still get marked isDirty=false below and look "synced"
                        // on this device forever, even though the server never actually received it,
                        // which meant it could never reach any other device either.
                        if (!response.isSuccessful) throw retrofit2.HttpException(response)
                        current.serverId
                    }

                    current = current.copy(serverId = serverId, isDirty = false)
                    dao.upsert(current)
                }

                val serverId = current.serverId
                if (current.photoDirty && current.localPhotoPath != null && serverId != null) {
                    val file = File(current.localPhotoPath)
                    if (file.exists()) {
                        val mimeType = java.net.URLConnection.guessContentTypeFromName(file.name)
                            ?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
                        val body = file.asRequestBody(mimeType.toMediaTypeOrNull())
                        val part = MultipartBody.Part.createFormData("photo", file.name, body)
                        val response = api.uploadPhoto(serverId, part)
                        dao.upsert(current.copy(photoUrl = response.photoUrl, photoDirty = false))
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("DeafIndividualRepository", "pushDirty failed for ${item.uuid}, will retry next sync", e)
            }
        }
    }
}
