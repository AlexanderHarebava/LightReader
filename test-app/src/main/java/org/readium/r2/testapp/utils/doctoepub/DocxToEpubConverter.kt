package org.readium.r2.testapp.utils.doctoepub

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.testapp.utils.fb2toepub.EpubGenerator
import java.io.File

object DocxToEpubConverter {
    suspend fun convert(inputFile: File, outputDir: File): File? = withContext(Dispatchers.IO) {
        try {
            val parser = DocxParser()
            val book = parser.parseFile(inputFile)
            val epubFile = File(outputDir, "${inputFile.nameWithoutExtension}.docx.epub")
            EpubGenerator().generateEpub(book, epubFile)
            if (epubFile.exists()) epubFile else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}