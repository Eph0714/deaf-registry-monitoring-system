package com.deafregistry.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SkillLevel { Skilled, `Semi-skilled`, Natural }
enum class MonitoringStatus { RV, BS, Transferred, Unlocated }
enum class Gender { Male, Female, Other }

@Entity(tableName = "deaf_individuals")
data class DeafIndividualEntity(
    @PrimaryKey val uuid: String,
    val serverId: Int?,
    val fullName: String,
    val photoUrl: String?,
    val localPhotoPath: String?,
    val birthDate: String?,
    val gender: String,
    val barangayId: Int,
    val barangayName: String,
    val purok: String?,
    val municipalityId: Int,
    val municipalityName: String,
    val latitude: Double?,
    val longitude: Double?,
    val skillLevel: String,
    val monitoringStatus: String,
    val assignedTeacherId: Int?,
    val assignedTeacherName: String?,
    val assignedTeacherContact: String?,
    val assignedDate: String?,
    val contactNumber: String?,
    val email: String?,
    val maritalStatus: String?,
    val emergencyContactName: String?,
    val emergencyContactNumber: String?,
    val notes: String?,
    val updatedAt: String,
    val isDirty: Boolean = false,
    val isDeleted: Boolean = false,
    val photoDirty: Boolean = false
)
