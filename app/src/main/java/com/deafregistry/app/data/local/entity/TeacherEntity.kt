package com.deafregistry.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "teachers")
data class TeacherEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val contactNumber: String?,
    val assignedCount: Int,
    val updatedAt: String
)
