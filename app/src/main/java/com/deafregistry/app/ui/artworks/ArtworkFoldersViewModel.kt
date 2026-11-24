package com.deafregistry.app.ui.artworks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deafregistry.app.data.remote.dto.ArtworkFolderDto
import com.deafregistry.app.data.repository.ArtworksRepository
import com.deafregistry.app.util.friendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ArtworkFoldersUiState(
    val folders: List<ArtworkFolderDto> = emptyList(),
    val search: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

// Root-level folders only (parentId = null) - a folder's own subfolders are shown and managed
// from ArtworkGalleryScreen/ArtworkGalleryViewModel instead, alongside that folder's images.
class ArtworkFoldersViewModel(private val repo: ArtworksRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtworkFoldersUiState())
    val uiState = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { repo.listFolders(search = _uiState.value.search) }
                .onSuccess { folders -> _uiState.value = _uiState.value.copy(folders = folders, isLoading = false) }
                .onFailure { _uiState.value = _uiState.value.copy(error = friendlyMessage(it), isLoading = false) }
        }
    }

    fun onSearchChange(value: String) {
        _uiState.value = _uiState.value.copy(search = value)
        load()
    }

    fun createFolder(name: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            runCatching { repo.createFolder(name) }
                .onSuccess { load(); onDone(true) }
                .onFailure { _uiState.value = _uiState.value.copy(error = friendlyMessage(it)); onDone(false) }
        }
    }

    fun renameFolder(id: Int, name: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            runCatching { repo.renameFolder(id, name) }
                .onSuccess { load(); onDone(true) }
                .onFailure { _uiState.value = _uiState.value.copy(error = friendlyMessage(it)); onDone(false) }
        }
    }

    fun deleteFolder(id: Int, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            runCatching { repo.deleteFolder(id) }
                .onSuccess { load(); onDone(true, null) }
                .onFailure { onDone(false, friendlyMessage(it)) }
        }
    }
}
