// kotlin-toolkit-develop/test-app/src/main/java/org/readium/r2/testapp/utils/ArchiveUtils.kt
package org.readium.r2.testapp.utils

import com.github.junrar.Archive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object ArchiveUtils {
    private val SUPPORTED_BOOK_EXTENSIONS = setOf(".epub", ".fb2", ".txt", ".docx", ".pdf")

    suspend fun extractBooks(archiveFile: File, outputDir: File): List<File> = withContext(Dispatchers.IO) {
        val extractedFiles = mutableListOf<File>()
        if (!outputDir.exists()) outputDir.mkdirs()

        try {
            when (archiveFile.extension.lowercase()) {
                "zip" -> extractZip(archiveFile, outputDir, extractedFiles)
                "rar" -> extractRar(archiveFile, outputDir, extractedFiles)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract archive: ${archiveFile.name}")
        }
        extractedFiles
    }

    private fun extractZip(zipFile: File, outputDir: File, result: MutableList<File>) {
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val outFile = File(outputDir, entry.name)
                    // Защита от Zip Slip атаки
                    if (outFile.canonicalPath.startsWith(outputDir.canonicalPath)) {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                        if (isBookFile(outFile)) result.add(outFile)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun extractRar(rarFile: File, outputDir: File, result: MutableList<File>) {
        Archive(rarFile).use { archive ->
            var entry = archive.nextFileHeader()
            while (entry != null) {
                if (!entry.isDirectory) {
                    val outFile = File(outputDir, entry.fileName.replace('\\', '/'))
                    if (outFile.canonicalPath.startsWith(outputDir.canonicalPath)) {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos -> archive.extractFile(entry, fos) }
                        if (isBookFile(outFile)) result.add(outFile)
                    }
                }
                entry = archive.nextFileHeader()
            }
        }
    }

    private fun isBookFile(file: File): Boolean {
        return SUPPORTED_BOOK_EXTENSIONS.any { file.name.endsWith(it, ignoreCase = true) }
    }
}