package com.deafregistry.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.deafregistry.app.data.local.entity.TeacherEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TeacherDao {
    @Query("SELECT * FROM teachers ORDER BY name ASC")
    fun observeAll(): Flow<List<TeacherEntity>>

    @Query("SELECT * FROM teachers ORDER BY name ASC")
    suspend fun getAll(): List<TeacherEntity>

    @Query("SELECT * FROM teachers WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): TeacherEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TeacherEntity>)

    @Query("DELETE FROM teachers")
    suspend fun clear()
}
