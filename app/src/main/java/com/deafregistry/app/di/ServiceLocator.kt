package com.deafregistry.app.di

import android.content.Context
import com.deafregistry.app.data.local.AppDatabase
import com.deafregistry.app.data.remote.ApiService
import com.deafregistry.app.data.remote.NetworkModule
import com.deafregistry.app.data.repository.AdminRepository
import com.deafregistry.app.data.repository.AuthRepository
import com.deafregistry.app.data.repository.DeafIndividualRepository
import com.deafregistry.app.data.repository.ReferenceDataRepository
import com.deafregistry.app.data.repository.RemarkRepository
import com.deafregistry.app.data.repository.ReportRepository
import com.deafregistry.app.data.repository.SettingsRepository
import com.deafregistry.app.data.repository.UserRepository
import com.deafregistry.app.data.repository.VisitRepository
import com.deafregistry.app.data.session.SessionManager
import com.deafregistry.app.data.sync.SyncManager
import com.deafregistry.app.util.NetworkMonitor

/**
 * Minimal hand-rolled service locator. Chosen over Hilt/Dagger to avoid annotation-processor
 * version coupling; the object graph here is small and static for the lifetime of the process.
 */
object ServiceLocator {

    @Volatile
    private var appContext: Context? = null

    val sessionManager: SessionManager by lazy { SessionManager(requireContext()) }
    val database: AppDatabase by lazy { AppDatabase.getInstance(requireContext()) }
    val api: ApiService by lazy { NetworkModule.create(sessionManager) }

    val referenceDataRepository: ReferenceDataRepository by lazy {
        ReferenceDataRepository(api, database.municipalityDao(), database.barangayDao(), database.teacherDao())
    }

    val deafIndividualRepository: DeafIndividualRepository by lazy {
        DeafIndividualRepository(
            requireContext(), api, database.deafIndividualDao(),
            database.municipalityDao(), database.barangayDao(), database.teacherDao()
        )
    }

    val visitRepository: VisitRepository by lazy {
        VisitRepository(api, database.visitDao(), database.deafIndividualDao())
    }

    val remarkRepository: RemarkRepository by lazy {
        RemarkRepository(api, database.remarkDao(), database.visitDao())
    }

    val authRepository: AuthRepository by lazy { AuthRepository(api, sessionManager) }
    val reportRepository: ReportRepository by lazy { ReportRepository(api) }
    val userRepository: UserRepository by lazy { UserRepository(api) }
    val adminRepository: AdminRepository by lazy { AdminRepository(api) }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(api, requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE))
    }

    val networkMonitor: NetworkMonitor by lazy { NetworkMonitor(requireContext()) }

    val syncManager: SyncManager by lazy {
        SyncManager(referenceDataRepository, deafIndividualRepository, visitRepository, remarkRepository, settingsRepository, authRepository)
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun requireContext(): Context =
        appContext ?: throw IllegalStateException("ServiceLocator.init() must be called from Application.onCreate()")
}
