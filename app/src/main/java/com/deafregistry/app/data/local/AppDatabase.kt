package com.deafregistry.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.deafregistry.app.data.local.dao.BarangayDao
import com.deafregistry.app.data.local.dao.DeafIndividualDao
import com.deafregistry.app.data.local.dao.MunicipalityDao
import com.deafregistry.app.data.local.dao.RemarkDao
import com.deafregistry.app.data.local.dao.TeacherDao
import com.deafregistry.app.data.local.dao.VisitDao
import com.deafregistry.app.data.local.entity.BarangayEntity
import com.deafregistry.app.data.local.entity.DeafIndividualEntity
import com.deafregistry.app.data.local.entity.MunicipalityEntity
import com.deafregistry.app.data.local.entity.RemarkEntity
import com.deafregistry.app.data.local.entity.TeacherEntity
import com.deafregistry.app.data.local.entity.VisitEntity

@Database(
    entities = [
        MunicipalityEntity::class,
        BarangayEntity::class,
        TeacherEntity::class,
        DeafIndividualEntity::class,
        VisitEntity::class,
        RemarkEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun municipalityDao(): MunicipalityDao
    abstract fun barangayDao(): BarangayDao
    abstract fun teacherDao(): TeacherDao
    abstract fun deafIndividualDao(): DeafIndividualDao
    abstract fun visitDao(): VisitDao
    abstract fun remarkDao(): RemarkDao

    companion object {
        const val DB_NAME = "deaf_registry.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }

        /** Closes and clears the cached instance so the underlying file can be safely overwritten (restore). */
        fun closeInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
