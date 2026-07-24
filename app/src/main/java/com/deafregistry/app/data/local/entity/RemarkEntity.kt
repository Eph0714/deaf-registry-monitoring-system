package com.deafregistry.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "remarks")
data class RemarkEntity(
    @PrimaryKey val uuid: String,
    val serverId: Int?,
    val visitUuid: String,
    val userId: Int? = null,
    val userName: String?,
    val remarkText: String,
    val createdAt: String,
    val isDirty: Boolean = false,
    val isDeleted: Boolean = false
)
