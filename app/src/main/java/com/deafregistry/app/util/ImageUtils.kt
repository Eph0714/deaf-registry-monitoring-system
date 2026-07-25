package com.deafregistry.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream

object ImageUtils {

    /**
     * Reads an image from [sourceUri] (camera capture or gallery pick, any of JPG/PNG/BMP/WEBP -
     * everything BitmapFactory decodes natively), corrects EXIF rotation, center-crops it to a
     * square, downsizes to [targetSizePx] x [targetSizePx], compresses as JPEG, and writes the
     * result to a fresh cache file. Used for profile photo updates so every stored/uploaded avatar
     * is a small, uniform square regardless of what the camera or gallery originally produced.
     */
    fun prepareSquareProfileImage(context: Context, sourceUri: Uri, targetSizePx: Int = 300, quality: Int = 85): File {
        val bytes = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Unable to read image")

        val rotationDegrees = runCatching {
            when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }.getOrDefault(0)

        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalArgumentException("Unable to decode image")
        if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        val side = minOf(bitmap.width, bitmap.height)
        val cropped = Bitmap.createBitmap(
            bitmap,
            (bitmap.width - side) / 2,
            (bitmap.height - side) / 2,
            side,
            side
        )
        val resized = Bitmap.createScaledBitmap(cropped, targetSizePx, targetSizePx, true)

        val outFile = File(context.cacheDir, "profile_${System.currentTimeMillis()}.jpg")
        FileOutputStream(outFile).use { out ->
            resized.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        return outFile
    }
}
