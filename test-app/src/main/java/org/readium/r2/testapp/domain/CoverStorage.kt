package org.readium.r2.testapp.domain

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.http.HttpClient
import org.readium.r2.shared.util.http.HttpError
import org.readium.r2.shared.util.http.HttpRequest
import org.readium.r2.shared.util.http.fetchWithDecoder
import org.readium.r2.testapp.utils.tryOrLog

class CoverStorage(
    private val appStorageDir: File,
    private val httpClient: HttpClient,
) {
    companion object {
        // Раньше обложка сжималась до 120×200 px — на экранах с высокой
        // плотностью она выглядела размытой. Храним до 600×900.
        private const val MAX_COVER_WIDTH = 600
        private const val MAX_COVER_HEIGHT = 900
        private const val JPEG_QUALITY = 90
    }

    suspend fun storeCover(publication: Publication, overrideUrl: AbsoluteUrl?): Try<File, Exception> {
        val coverBitmap: Bitmap? = overrideUrl?.fetchBitmap()
            ?: publication.cover()
        return try {
            Try.success(storeCover(coverBitmap))
        } catch (e: Exception) {
            Try.failure(e)
        }
    }

    private suspend fun storeCover(cover: Bitmap?): File =
        withContext(Dispatchers.IO) {
            val coverImageFile = File(coverDir(), "${UUID.randomUUID()}.jpg")
            val scaled = cover?.let { scaleDown(it) }
            FileOutputStream(coverImageFile).use { fos ->
                scaled?.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos)
                fos.flush()
            }
            coverImageFile
        }

    /**
     * Уменьшает битмап до лимита с сохранением пропорций.
     * Маленькие картинки не масштабируются вовсе.
     */
    private fun scaleDown(src: Bitmap): Bitmap {
        val ratio = minOf(
            MAX_COVER_WIDTH.toFloat() / src.width,
            MAX_COVER_HEIGHT.toFloat() / src.height
        )
        if (ratio >= 1f) return src
        val w = (src.width * ratio).toInt().coerceAtLeast(1)
        val h = (src.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    private suspend fun AbsoluteUrl.fetchBitmap(): Bitmap? =
        tryOrLog {
            when {
                isFile -> toFile()?.toBitmap()
                isHttp -> httpClient.fetchBitmap(HttpRequest(this)).getOrNull()
                else -> null
            }
        }

    private suspend fun File.toBitmap(): Bitmap? =
        withContext(Dispatchers.IO) {
            tryOrLog {
                BitmapFactory.decodeFile(path)
            }
        }

    private suspend fun HttpClient.fetchBitmap(request: HttpRequest): Try<Bitmap, HttpError> =
        fetchWithDecoder(request) { response ->
            BitmapFactory.decodeByteArray(response.body, 0, response.body.size)
        }

    private fun coverDir(): File =
        File(appStorageDir, "covers/")
            .apply { if (!exists()) mkdirs() }
}