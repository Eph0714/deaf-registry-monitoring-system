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
     *
     * This is the only method that pushes locally-queued (dirty) records to the server - it
     * must only ever be called from the user tapping the Sync button, never automatically.
     * While offline, edits keep saving locally via Room regardless; they just queue as dirty
     * until the user explicitly syncs.
     */
    suspend fun sync() {
        // Each step is independent - reference data or profile refresh failing (e.g. a slow
        // Render cold start timing out on an early call) must not prevent deaf individuals from
        // being pushed/pulled, which is the part users actually notice as "Sync doesn't work".
        runCatching { referenceDataRepository.refreshAll() }
        runCatching { settingsRepository.refreshOverdueDays() }
        runCatching { settingsRepository.refreshTheme() }
        runCatching { authRepository.refreshProfile() }
        deafIndividualRepository.pushDirty()
        visitRepository.pushDirty()
        remarkRepository.pushDirty()
        deafIndividualRepository.refreshFromServer()
        // Must come after the individuals refresh above - it needs each individual's serverId
        // already mapped locally to attach a pulled visit to the right uuid.
        runCatching { visitRepository.refreshAll() }
    }

    /**
     * Downloads the latest reference data and deaf-individual records without pushing any
     * local edits. Safe to call automatically (e.g. on screen load) since it can never send
     * locally-queued changes to the server - only sync() does that.
     */
    suspend fun pull() {
        runCatching { referenceDataRepository.refreshAll() }
        runCatching { settingsRepository.refreshOverdueDays() }
        runCatching { settingsRepository.refreshTheme() }
        runCatching { authRepository.refreshProfile() }
        deafIndividualRepository.refreshFromServer()
        runCatching { visitRepository.refreshAll() }
    }

    /** Count of locally-queued (not-yet-synced) records across all entity types. */
    suspend fun pendingCount(): Int =
        deafIndividualRepository.dirtyCount() + visitRepository.dirtyCount() + remarkRepository.dirtyCount()
}
