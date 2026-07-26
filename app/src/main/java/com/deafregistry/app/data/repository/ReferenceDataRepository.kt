package com.deafregistry.app.data.repository

import com.deafregistry.app.data.local.dao.BarangayDao
import com.deafregistry.app.data.local.dao.MunicipalityDao
import com.deafregistry.app.data.local.dao.TeacherDao
import com.deafregistry.app.data.local.entity.BarangayEntity
import com.deafregistry.app.data.local.entity.MunicipalityEntity
import com.deafregistry.app.data.local.entity.TeacherEntity
import com.deafregistry.app.data.remote.ApiService
import com.deafregistry.app.data.remote.dto.BarangayRequest
import com.deafregistry.app.data.remote.dto.BulkReassignRequest
import com.deafregistry.app.data.remote.dto.NameRequest
import com.deafregistry.app.data.remote.dto.TeacherRequest
import kotlinx.coroutines.flow.Flow

class ReferenceDataRepository(
    private val api: ApiService,
    private val municipalityDao: MunicipalityDao,
    private val barangayDao: BarangayDao,
    private val teacherDao: TeacherDao
) {
    fun observeMunicipalities(): Flow<List<MunicipalityEntity>> = municipalityDao.observeAll()
    fun observeMunicipalitiesWithCounts(): Flow<List<com.deafregistry.app.data.local.dao.MunicipalityWithCount>> =
        municipalityDao.observeAllWithCounts()
    fun observeBarangays(municipalityId: Int): Flow<List<BarangayEntity>> = barangayDao.observeForMunicipality(municipalityId)
    fun observeBarangaysWithCounts(municipalityId: Int): Flow<List<com.deafregistry.app.data.local.dao.BarangayWithCount>> =
        barangayDao.observeForMunicipalityWithCounts(municipalityId)
    fun observeAllBarangaysWithCounts(): Flow<List<com.deafregistry.app.data.local.dao.BarangayWithCount>> =
        barangayDao.observeAllWithCounts()
    fun observeTeachers(): Flow<List<TeacherEntity>> = teacherDao.observeAll()

    suspend fun getBarangaysForMunicipality(municipalityId: Int): List<BarangayEntity> =
        barangayDao.getForMunicipality(municipalityId)

    suspend fun getAllBarangaysCached(): List<BarangayEntity> = barangayDao.getAll()
    suspend fun getAllTeachersCached(): List<TeacherEntity> = teacherDao.getAll()
    suspend fun getAllMunicipalitiesCached(): List<MunicipalityEntity> = municipalityDao.getAll()

    suspend fun refreshAll() {
        val municipalities = api.getMunicipalities()
        municipalityDao.upsertAll(municipalities.map {
            MunicipalityEntity(id = it.id, name = it.name, updatedAt = it.updatedAt)
        })

        val barangays = api.getBarangays()
        barangayDao.upsertAll(barangays.map {
            BarangayEntity(
                id = it.id,
                name = it.name,
                municipalityId = it.municipalityId,
                municipalityName = it.municipalityName,
                updatedAt = it.updatedAt
            )
        })

        val teachers = api.getTeachers()
        teacherDao.upsertAll(teachers.map {
            TeacherEntity(
                id = it.id,
                name = it.name,
                contactNumber = it.contactNumber,
                assignedCount = it.assignedCount,
                updatedAt = it.updatedAt
            )
        })
    }

    suspend fun createMunicipality(name: String) = api.createMunicipality(NameRequest(name))
    suspend fun updateMunicipality(id: Int, name: String) = api.updateMunicipality(id, NameRequest(name))
    suspend fun deleteMunicipality(id: Int) = api.deleteMunicipality(id)

    suspend fun createBarangay(name: String, municipalityId: Int) = api.createBarangay(BarangayRequest(name, municipalityId))
    suspend fun updateBarangay(id: Int, name: String, municipalityId: Int) = api.updateBarangay(id, BarangayRequest(name, municipalityId))
    suspend fun deleteBarangay(id: Int) = api.deleteBarangay(id)

    suspend fun createTeacher(name: String, contactNumber: String?) = api.createTeacher(TeacherRequest(name, contactNumber))
    suspend fun updateTeacher(id: Int, name: String, contactNumber: String?) = api.updateTeacher(id, TeacherRequest(name, contactNumber))
    suspend fun deleteTeacher(id: Int) = api.deleteTeacher(id)

    suspend fun getAssignmentHistory(deafServerId: Int) = api.getAssignmentHistory(deafServerId)

    /** Unauthenticated - for the pre-login Sign Up form's Municipality/Barangay dropdowns only. */
    suspend fun getPublicMunicipalities() = api.getPublicMunicipalities()
    suspend fun getPublicBarangays(municipalityId: Int) = api.getPublicBarangays(municipalityId)

    suspend fun bulkReassignTeacher(fromTeacherId: Int, toTeacherId: Int, reason: String?) =
        api.bulkReassignTeacher(BulkReassignRequest(fromTeacherId, toTeacherId, reason))
}
