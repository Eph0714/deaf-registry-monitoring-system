package com.deafregistry.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "visits")
data class VisitEntity(
    @PrimaryKey val uuid: String,
    val serverId: Int?,
    val deafIndividualUuid: String,
    val visitDateTime: String,
    val latitude: Double?,
    val longitude: Double?,
    val conductorId: Int?,
    val conductorName: String?,
    val updatedAt: String,
    val isDirty: Boolean = false,
    val isDeleted: Boolean = false
)
