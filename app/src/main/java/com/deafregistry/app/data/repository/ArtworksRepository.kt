package com.deafregistry.app.data.repository

import com.deafregistry.app.data.remote.ApiService
import com.deafregistry.app.data.remote.dto.ArtworkDto
import com.deafregistry.app.data.remote.dto.ArtworkFolderDto
import com.deafregistry.app.data.remote.dto.ArtworkNotificationDto
import com.deafregistry.app.data.remote.dto.ArtworkRequestDto
import com.deafregistry.app.data.remote.dto.ArtworkUploadRequestResultDto
import com.deafregistry.app.data.remote.dto.ArtworkUploadResultDto
import com.deafregistry.app.data.remote.dto.CreateFolderRequest
import com.deafregistry.app.data.remote.dto.MarkNotificationsReadRequest
import com.deafregistry.app.data.remote.dto.MoveArtworkRequest
import com.deafregistry.app.data.remote.dto.RejectRequestBody
import com.deafregistry.app.data.remote.dto.RenameArtworkRequest
import com.deafregistry.app.data.remote.dto.RenameFolderRequest
import com.deafregistry.app.util.ProgressRequestBody
import com.deafregistry.app.data.remote.dto.SubmitDeleteRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

// Thin wrapper directly over ApiService, no Room/offline sync - artwork galleries are inherently
// online (thumbnails, zoom, admin review all require network), same template as ChatRepository.
class ArtworksRepository(private val api: ApiService) {

    // Folders
    // Folders (and artworks, see listArtworks) are always returned name-ascending by the server -
    // not a client-configurable sort.
    suspend fun listFolders(search: String? = null, parentId: Int? = null): List<ArtworkFolderDto> =
        api.getArtworkFolders(search?.takeIf { it.isNotBlank() }, parentId)

    suspend fun getFolder(id: Int): ArtworkFolderDto = api.getArtworkFolder(id)
    suspend fun createFolder(name: String, parentId: Int? = null): ArtworkFolderDto =
        api.createArtworkFolder(CreateFolderRequest(name, parentId))
    suspend fun renameFolder(id: Int, name: String): ArtworkFolderDto = api.renameArtworkFolder(id, RenameFolderRequest(name))
    suspend fun deleteFolder(id: Int) = api.deleteArtworkFolder(id)

    // Artworks
    suspend fun listArtworks(folderId: Int, includeAll: Boolean = false): List<ArtworkDto> =
        api.getArtworks(folderId, includeAll.takeIf { it })

    suspend fun getArtwork(id: Int): ArtworkDto = api.getArtwork(id)
    suspend fun listRecycleBin(): List<ArtworkDto> = api.getArtworkRecycleBin()

    // onProgress reports cumulative bytes sent across the whole batch, not per file - that's
    // what a single overall progress bar needs.
    private fun filesToParts(files: List<File>, onProgress: (sent: Long, total: Long) -> Unit): List<MultipartBody.Part> {
        val totalBytes = files.sumOf { it.length() }
        val sentPerFile = LongArray(files.size)
        return files.mapIndexed { index, file ->
            val mimeType = java.net.URLConnection.guessContentTypeFromName(file.name)
                ?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
            val body = file.asRequestBody(mimeType.toMediaTypeOrNull())
            val progressBody = ProgressRequestBody(body) { written, _ ->
                sentPerFile[index] = written
                onProgress(sentPerFile.sum(), totalBytes)
            }
            MultipartBody.Part.createFormData("images", file.name, progressBody)
        }
    }

    // Admin-only, enforced server-side too - publishes immediately, no approval step.
    suspend fun uploadDirect(folderId: Int, files: List<File>, onProgress: (sent: Long, total: Long) -> Unit = { _, _ -> }): ArtworkUploadResultDto =
        api.uploadArtworksDirect(folderId, filesToParts(files, onProgress))

    // Regular-user path - server forces the resulting artworks to 'pending', invisible until an
    // admin approves the grouping request this creates.
    suspend fun uploadRequest(
        folderId: Int,
        files: List<File>,
        note: String? = null,
        onProgress: (sent: Long, total: Long) -> Unit = { _, _ -> }
    ): ArtworkUploadRequestResultDto =
        api.uploadArtworksRequest(folderId, filesToParts(files, onProgress), note?.toRequestBody("text/plain".toMediaTypeOrNull()))

    suspend fun renameArtwork(id: Int, fileName: String) = api.renameArtwork(id, RenameArtworkRequest(fileName))
    suspend fun moveArtwork(id: Int, folderId: Int) = api.moveArtwork(id, MoveArtworkRequest(folderId))
    suspend fun deleteArtworkDirect(id: Int) = api.deleteArtwork(id)
    suspend fun restoreArtwork(id: Int) = api.restoreArtwork(id)
    suspend fun permanentlyDelete(id: Int) = api.permanentlyDeleteArtwork(id)

    // Requests / approval center
    suspend fun submitDeleteRequest(artworkIds: List<Int>, reason: String) =
        api.submitArtworkDeleteRequest(SubmitDeleteRequestBody(artworkIds, reason))

    suspend fun listRequests(status: String? = null, type: String? = null): List<ArtworkRequestDto> =
        api.getArtworkRequests(status, type)

    suspend fun listMyRequests(status: String? = null): List<ArtworkRequestDto> = api.getMyArtworkRequests(status)
    suspend fun getRequest(id: Int): ArtworkRequestDto = api.getArtworkRequest(id)
    suspend fun approveRequest(id: Int) = api.approveArtworkRequest(id)
    suspend fun rejectRequest(id: Int, reason: String) = api.rejectArtworkRequest(id, RejectRequestBody(reason))
    suspend fun cancelRequest(id: Int) = api.cancelArtworkRequest(id)

    // Notifications
    suspend fun getNotifications(): List<ArtworkNotificationDto> = api.getNotifications()
    suspend fun markNotificationsRead(ids: List<Int>? = null) = api.markNotificationsRead(MarkNotificationsReadRequest(ids))
}
