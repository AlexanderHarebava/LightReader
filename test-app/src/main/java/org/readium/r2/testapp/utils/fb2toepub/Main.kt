package org.readium.r2.testapp.utils.fb2toepub

import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.size != 2) {
        println("Usage: <input.fb2> <output.epub>")
        exitProcess(1)
    }
    val inputFile = File(args[0])
    val outputFile = File(args[1])

    if (!inputFile.exists()) {
        println("Error: Input file does not exist.")
        exitProcess(1)
    }

    try {
        println("Converting '${inputFile.name}' to EPUB format...")
        val parser = FB2Parser()
        val book = parser.parseFile(inputFile)

        println("Book title: ${book.title}")
        println("Authors: ${book.authorsString()}")
        println("Images found: ${book.images.size}")
        println("Chapters: ${book.chapters.size}")

        val generator = EpubGenerator()
        generator.generateEpub(book, outputFile)

        println("Successfully converted to '${outputFile.name}'")
    } catch (e: Exception) {
        println("Error during conversion: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    }
}