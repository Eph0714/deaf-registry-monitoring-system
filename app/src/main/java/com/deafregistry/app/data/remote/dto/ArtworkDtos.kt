package com.deafregistry.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ArtworkFolderDto(
    val id: Int,
    @SerializedName("parent_id") val parentId: Int?,
    val name: String,
    @SerializedName("created_by") val createdBy: Int?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("image_count") val imageCount: Int,
    @SerializedName("subfolder_count") val subfolderCount: Int,
    @SerializedName("cover_url") val coverUrl: String?
)

data class ArtworkDto(
    val id: Int,
    @SerializedName("folder_id") val folderId: Int,
    @SerializedName("file_name") val fileName: String,
    @SerializedName("public_url_original") val publicUrlOriginal: String,
    @SerializedName("public_url_optimized") val publicUrlOptimized: String,
    @SerializedName("public_url_thumbnail") val publicUrlThumbnail: String,
    val format: String,
    @SerializedName("width_px") val widthPx: Int,
    @SerializedName("height_px") val heightPx: Int,
    @SerializedName("original_size_bytes") val originalSizeBytes: Int,
    @SerializedName("optimized_size_bytes") val optimizedSizeBytes: Int,
    @SerializedName("publish_status") val publishStatus: String,
    @SerializedName("deleted_status") val deletedStatus: String,
    @SerializedName("uploaded_by") val uploadedBy: Int?,
    @SerializedName("uploaded_by_name") val uploadedByName: String?,
    @SerializedName("uploaded_at") val uploadedAt: String,
    @SerializedName("published_at") val publishedAt: String?,
    @SerializedName("deleted_at") val deletedAt: String?
)

data class ArtworkUploadResultDto(
    @SerializedName("artworkIds") val artworkIds: List<Int>
)

data class ArtworkUploadRequestResultDto(
    @SerializedName("requestId") val requestId: Int,
    @SerializedName("artworkIds") val artworkIds: List<Int>
)

data class MoveArtworkRequest(@SerializedName("folderId") val folderId: Int)
data class RenameArtworkRequest(@SerializedName("fileName") val fileName: String)
data class RenameFolderRequest(val name: String)
data class CreateFolderRequest(val name: String, val parentId: Int? = null)
data class SubmitDeleteRequestBody(@SerializedName("artworkIds") val artworkIds: List<Int>, val reason: String)
data class RejectRequestBody(val reason: String)

data class ArtworkRequestItemDto(
    val id: Int,
    @SerializedName("file_name") val fileName: String,
    @SerializedName("public_url_thumbnail") val publicUrlThumbnail: String,
    @SerializedName("public_url_optimized") val publicUrlOptimized: String,
    val format: String
)

data class ArtworkRequestDto(
    val id: Int,
    @SerializedName("request_type") val requestType: String,
    @SerializedName("folder_id") val folderId: Int,
    @SerializedName("folder_name") val folderName: String?,
    @SerializedName("requested_by") val requestedBy: Int?,
    @SerializedName("requested_by_name") val requestedByName: String?,
    val reason: String?,
    val status: String,
    @SerializedName("rejection_reason") val rejectionReason: String?,
    @SerializedName("decided_by") val decidedBy: Int?,
    @SerializedName("decided_by_name") val decidedByName: String?,
    @SerializedName("decided_at") val decidedAt: String?,
    @SerializedName("created_at") val createdAt: String,
    val items: List<ArtworkRequestItemDto>
)

data class ArtworkNotificationDto(
    val id: Int,
    @SerializedName("user_id") val userId: Int,
    val type: String,
    @SerializedName("entity_type") val entityType: String,
    @SerializedName("entity_id") val entityId: Int?,
    val message: String,
    val status: String,
    @SerializedName("created_at") val createdAt: String
)

data class MarkNotificationsReadRequest(val ids: List<Int>? = null)
