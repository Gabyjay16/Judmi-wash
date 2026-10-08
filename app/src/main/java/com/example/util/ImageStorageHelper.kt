package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object ImageStorageHelper {

    fun getPhotosDirectory(context: Context): File {
        val dir = File(context.filesDir, "laundry_photos")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Creates a temporary file and returns its content URI for camera capture.
     */
    fun createTempCameraUri(context: Context): Pair<Uri, File> {
        val dir = getPhotosDirectory(context)
        val file = File(dir, "snap_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Pair(uri, file)
    }

    /**
     * Copies a picked Uri (from PhotoPicker or Gallery) into app internal storage and returns the local file path URI string.
     */
    suspend fun saveUriToInternalStorage(context: Context, sourceUri: Uri): String = withContext(Dispatchers.IO) {
        val dir = getPhotosDirectory(context)
        val destFile = File(dir, "dress_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
        
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        destFile.toURI().toString()
    }

    /**
     * Delete physical image file from storage if it exists in internal files.
     */
    fun deleteImageFile(imageUriString: String) {
        try {
            val uri = Uri.parse(imageUriString)
            val path = uri.path
            if (path != null) {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Loads a downsampled Bitmap from a file URI or android Uri for Gemini API transmission.
     */
    suspend fun loadDownscaledBitmap(context: Context, uriString: String, maxDimension: Int = 1024): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            val inputStream: InputStream? = if (uri.scheme == "file") {
                File(uri.path ?: "").inputStream()
            } else {
                context.contentResolver.openInputStream(uri)
            }

            inputStream?.use { stream ->
                val bytes = stream.readBytes()
                val boundsOnly = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOnly)

                var sampleSize = 1
                while ((boundsOnly.outWidth / sampleSize) > maxDimension || (boundsOnly.outHeight / sampleSize) > maxDimension) {
                    sampleSize *= 2
                }

                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Convert Bitmap to JPEG Base64 string for Gemini API multimodal payload.
     */
    fun bitmapToBase64(bitmap: Bitmap, quality: Int = 85): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
}
