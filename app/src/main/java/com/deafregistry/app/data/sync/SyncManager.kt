package com.deafregistry.app.data.sync

import com.deafregistry.app.data.repository.AuthRepository
import com.deafregistry.app.data.repository.DeafIndividualRepository
import com.deafregistry.app.data.repository.ReferenceDataRepository
import com.deafregistry.app.data.repository.RemarkRepository
import com.deafregistry.app.data.repository.SettingsRepository
import com.deafregistry.app.data.repository.VisitRepository

class SyncManager(
    private val referenceDataRepository: ReferenceDataRepository,
    private val deafIndividualRepository: DeafIndividualRepository,
    private val visitRepository: VisitRepository,
    private val remarkRepository: RemarkRepository,
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository
) {
    /**
     * Push order matters: a deaf individual must obtain a serverId before its visits can be
     * pushed, and a visit must obtain a serverId before its remarks can be pushed.
     */
    suspend fun sync() {
        referenceDataRepository.refreshAll()
        runCatching { settingsRepository.refreshOverdueDays() }
        runCatching { authRepository.refreshProfile() }
        deafIndividualRepository.pushDirty()
        visitRepository.pushDirty()
        remarkRepository.pushDirty()
        deafIndividualRepository.refreshFromServer()
    }

    /** Count of locally-queued (not-yet-synced) records across all entity types. */
    suspend fun pendingCount(): Int =
        deafIndividualRepository.dirtyCount() + visitRepository.dirtyCount() + remarkRepository.dirtyCount()
}
