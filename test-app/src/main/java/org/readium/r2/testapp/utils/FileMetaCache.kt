package org.readium.r2.testapp.utils

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap


object FileMetaCache {

    private class Meta(val size: Long, val lastModified: Long)

    private val metas = ConcurrentHashMap<String, Meta>()
    private val dates = ConcurrentHashMap<String, String>()

    private fun meta(href: String): Meta = metas.getOrPut(href) {
        runCatching {
            val f = File(href.removePrefix("file://"))
            if (f.exists()) Meta(f.length(), f.lastModified()) else Meta(0L, 0L)
        }.getOrDefault(Meta(0L, 0L))
    }

    fun warm(file: File) {
        val href = android.net.Uri.fromFile(file).toString()
        metas[href] = Meta(file.length(), file.lastModified())
    }

    fun size(href: String): Long = meta(href).size
    fun lastModified(href: String): Long = meta(href).lastModified

    fun sizeFormatted(href: String): String {
        val bytes = size(href)
        if (bytes <= 0L) return "–"
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${"%.1f".format(Locale.US, bytes / 1024.0 / 1024.0)} MB"
        }
    }

    fun dateFormatted(href: String): String {
        val ts = lastModified(href)
        if (ts <= 0L) return "–"
        return dates.getOrPut(href) {
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(ts))
        }
    }

    fun invalidate(href: String) { metas.remove(href); dates.remove(href) }
    fun invalidateAll() { metas.clear(); dates.clear() }
}

/** Кэш процента прочтения — парсинг JSON один раз на строку progression. */
object ProgressCache {
    private val cache = ConcurrentHashMap<String, Int>()

    fun percent(progression: String?): Int {
        val json = progression?.takeIf { it.isNotEmpty() } ?: return 0
        return cache.getOrPut(json) {
            runCatching {
                val locations = org.json.JSONObject(json).optJSONObject("locations")
                ((locations?.optDouble("totalProgression", 0.0) ?: 0.0) * 100)
                    .toInt().coerceIn(0, 100)
            }.getOrDefault(0)
        }
    }
}