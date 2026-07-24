package com.deafregistry.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "municipalities")
data class MunicipalityEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val updatedAt: String
)
