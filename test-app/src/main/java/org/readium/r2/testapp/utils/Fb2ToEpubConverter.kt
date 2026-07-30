package org.readium.r2.testapp.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.testapp.utils.fb2toepub.EpubGenerator
import org.readium.r2.testapp.utils.fb2toepub.FB2Parser
import timber.log.Timber
import java.io.File

object Fb2ToEpubConverter {


    suspend fun convert(fb2File: File, outputDir: File): File? = withContext(Dispatchers.IO) {
        try {
            if (!outputDir.exists()) outputDir.mkdirs()

            val parser = FB2Parser()
            val book = parser.parseFile(fb2File)

            val epubFile = File(outputDir, "${fb2File.nameWithoutExtension}.epub")
            val generator = EpubGenerator()
            generator.generateEpub(book, epubFile)

            epubFile
        } catch (e: Exception) {
            Timber.e(e, "FB2 conversion failed")
            null
        }
    }
}