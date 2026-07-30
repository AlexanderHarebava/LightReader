package org.readium.r2.testapp.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object CoverLoader {

    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    fun load(path: String?, imageView: ImageView) {
        load(path, null, imageView)
    }

    fun load(
        path: String?,
        title: String?,
        imageView: ImageView
    ) {
        val effectivePath = path?.takeIf { it.isNotBlank() }

        val reqWidth = imageView.width.takeIf { it > 0 } ?: 160
        val reqHeight = imageView.height.takeIf { it > 0 } ?: 240

        val cacheKey = effectivePath
            ?: "generated:${title.orEmpty()}:${reqWidth}x${reqHeight}"

        cache.get(cacheKey)?.let { bitmap ->
            imageView.tag = cacheKey
            imageView.setImageBitmap(bitmap)
            return
        }

        imageView.tag = cacheKey
        imageView.setImageBitmap(null)

        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                val decoded = effectivePath?.let { decodeCover(it) }

                decoded ?: CoverGenerator.generate(
                    title = title?.trim()?.takeIf { it.isNotBlank() } ?: "Book",
                    widthPx = reqWidth,
                    heightPx = reqHeight
                )
            }

            if (imageView.tag == cacheKey) {
                cache.put(cacheKey, bitmap)
                imageView.setImageBitmap(bitmap)
            }
        }
    }

    private fun decodeCover(path: String): Bitmap? {
        val file = File(path)

        if (!file.exists() || file.length() < 100) {
            return null
        }

        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            BitmapFactory.decodeFile(path, options)

            options.inSampleSize = calculateInSampleSize(
                options = options,
                reqWidth = 160,
                reqHeight = 240
            )

            options.inJustDecodeBounds = false

            BitmapFactory.decodeFile(path, options)
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }
}