package com.deafregistry.app.ui.artworks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deafregistry.app.data.remote.dto.ArtworkDto
import com.deafregistry.app.data.remote.dto.ArtworkFolderDto
import com.deafregistry.app.data.repository.ArtworksRepository
import com.deafregistry.app.util.friendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ArtworkGalleryUiState(
    val folderName: String = "",
    val subfolders: List<ArtworkFolderDto> = emptyList(),
    val artworks: List<ArtworkDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectionMode: Boolean = false,
    val selectedIds: Set<Int> = emptySet()
)

// A folder shows both its own subfolders and its own direct images - subfolders are created,
// renamed and deleted from here rather than a separate screen, since a folder's contents (of
// either kind) only ever need managing from inside that same folder.
class ArtworkGalleryViewModel(
    private val folderId: Int,
    private val repo: ArtworksRepository,
    private val isAdmin: Boolean
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtworkGalleryUiState())
    val uiState = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching {
                val folder = repo.getFolder(folderId)
                val subfolders = repo.listFolders(parentId = folderId)
                val artworks = repo.listArtworks(folderId, includeAll = isAdmin)
                Triple(folder, subfolders, artworks)
            }.onSuccess { (folder, subfolders, artworks) ->
                _uiState.value = _uiState.value.copy(
                    folderName = folder.name,
                    subfolders = subfolders,
                    artworks = artworks,
                    isLoading = false
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(error = friendlyMessage(it), isLoading = false)
            }
        }
    }

    fun createSubfolder(name: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            runCatching { repo.createFolder(name, parentId = folderId) }
                .onSuccess { load(); onDone(true) }
                .onFailure { _uiState.value = _uiState.value.copy(error = friendlyMessage(it)); onDone(false) }
        }
    }

    fun renameSubfolder(id: Int, name: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            runCatching { repo.renameFolder(id, name) }
                .onSuccess { load(); onDone(true) }
                .onFailure { _uiState.value = _uiState.value.copy(error = friendlyMessage(it)); onDone(false) }
        }
    }

    fun deleteSubfolder(id: Int, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            runCatching { repo.deleteFolder(id) }
                .onSuccess { load(); onDone(true, null) }
                .onFailure { onDone(false, friendlyMessage(it)) }
        }
    }

    fun renameArtwork(id: Int, fileName: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            runCatching { repo.renameArtwork(id, fileName) }
                .onSuccess { load(); onDone(true) }
                .onFailure { _uiState.value = _uiState.value.copy(error = friendlyMessage(it)); onDone(false) }
        }
    }

    fun submitDeleteRequest(artworkId: Int, reason: String, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            runCatching { repo.submitDeleteRequest(listOf(artworkId), reason) }
                .onSuccess { load(); onDone(true, null) }
                .onFailure { onDone(false, friendlyMessage(it)) }
        }
    }

    // Admin-only direct delete - bypasses approval, moves straight to the Recycle Bin.
    fun deleteArtworkDirect(artworkId: Int, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            runCatching { repo.deleteArtworkDirect(artworkId) }
                .onSuccess { load(); onDone(true, null) }
                .onFailure { onDone(false, friendlyMessage(it)) }
        }
    }

    // --- Multi-select ---

    fun enterSelectionMode(initialId: Int) {
        _uiState.value = _uiState.value.copy(selectionMode = true, selectedIds = setOf(initialId))
    }

    fun toggleSelection(id: Int) {
        val updated = _uiState.value.selectedIds.let { if (id in it) it - id else it + id }
        _uiState.value = _uiState.value.copy(selectedIds = updated, selectionMode = updated.isNotEmpty())
    }

    fun exitSelectionMode() {
        _uiState.value = _uiState.value.copy(selectionMode = false, selectedIds = emptySet())
    }

    // Admin-only, mirrors deleteArtworkDirect but for every selected image at once - sequential
    // (not parallel) so a slow/failing item can't be confused with another's result, and each
    // one is independently isolated so one failure doesn't abort the rest of the batch.
    fun bulkDeleteDirect(onDone: (succeeded: Int, failed: Int) -> Unit) {
        val ids = _uiState.value.selectedIds.toList()
        viewModelScope.launch {
            var succeeded = 0
            var failed = 0
            for (id in ids) {
                runCatching { repo.deleteArtworkDirect(id) }
                    .onSuccess { succeeded++ }
                    .onFailure { failed++ }
            }
            exitSelectionMode()
            load()
            onDone(succeeded, failed)
        }
    }

    // Regular-user path - one grouped request covering every selected image, same as a
    // single-image deletion request (artwork_requests already groups multiple artwork ids under
    // one reviewable request - see artwork_request_items).
    fun bulkSubmitDeleteRequest(reason: String, onDone: (Boolean, String?) -> Unit) {
        val ids = _uiState.value.selectedIds.toList()
        viewModelScope.launch {
            runCatching { repo.submitDeleteRequest(ids, reason) }
                .onSuccess { exitSelectionMode(); load(); onDone(true, null) }
                .onFailure { onDone(false, friendlyMessage(it)) }
        }
    }
}
