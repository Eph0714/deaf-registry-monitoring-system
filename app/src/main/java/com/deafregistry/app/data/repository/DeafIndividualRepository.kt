package com.deafregistry.app.data.repository

import android.content.Context
import com.deafregistry.app.data.local.dao.BarangayDao
import com.deafregistry.app.data.local.dao.DeafIndividualDao
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

    fun search(query: String): Flow<List<DeafIndividualEntity>> = dao.search(query)

    fun observeCountForMunicipality(municipalityId: Int): Flow<Int> = dao.observeCountForMunicipality(municipalityId)

    suspend fun getById(uuid: String): DeafIndividualEntity? = dao.getById(uuid)

    suspend fun dirtyCount(): Int = dao.getDirty().size

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
        dao.upsertAll(entities)
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

    /** Pushes all locally-dirty records to the server. Best-effort; failures are skipped and retried next sync. */
    suspend fun pushDirty() {
        for (item in dao.getDirty()) {
            try {
                if (item.isDeleted) {
                    item.serverId?.let { api.deleteDeafIndividual(it) }
                    dao.hardDelete(item.uuid)
                    continue
                }

                val request = DeafIndividualRequest(
                    uuid = item.uuid,
                    full_name = item.fullName,
                    birth_date = item.birthDate,
                    gender = item.gender,
                    barangay_id = item.barangayId,
                    purok = item.purok,
                    municipality_id = item.municipalityId,
                    latitude = item.latitude,
                    longitude = item.longitude,
                    skill_level = item.skillLevel,
                    monitoring_status = item.monitoringStatus,
                    assigned_teacher_id = item.assignedTeacherId,
                    assigned_date = item.assignedDate,
                    contact_number = item.contactNumber,
                    email = item.email,
                    marital_status = item.maritalStatus,
                    emergency_contact_name = item.emergencyContactName,
                    emergency_contact_number = item.emergencyContactNumber,
                    notes = item.notes
                )

                val serverId = if (item.serverId == null) {
                    api.createDeafIndividual(request).id
                } else {
                    api.updateDeafIndividual(item.serverId, request)
                    item.serverId
                }

                var updated = item.copy(serverId = serverId, isDirty = false)

                if (item.photoDirty && item.localPhotoPath != null) {
                    val file = File(item.localPhotoPath)
                    if (file.exists()) {
                        val body = file.asRequestBody("image/*".toMediaTypeOrNull())
                        val part = MultipartBody.Part.createFormData("photo", file.name, body)
                        val response = api.uploadPhoto(serverId, part)
                        updated = updated.copy(photoUrl = response.photoUrl, photoDirty = false)
                    }
                }

                dao.upsert(updated)
            } catch (e: Exception) {
                // Leave marked dirty; will retry on next sync pass.
            }
        }
    }
}
