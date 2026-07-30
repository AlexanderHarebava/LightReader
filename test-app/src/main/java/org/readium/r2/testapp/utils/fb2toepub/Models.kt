package org.readium.r2.testapp.utils.fb2toepub

import java.util.*

data class Author(
    val firstName: String = "",
    val middleName: String = "",
    val lastName: String = "",
    val nickname: String = ""
) {
    fun fullName(): String {
        val parts = listOfNotNull(
            firstName.takeIf { it.isNotBlank() },
            middleName.takeIf { it.isNotBlank() },
            lastName.takeIf { it.isNotBlank() }
        )
        return if (parts.isNotEmpty()) parts.joinToString(" ")
        else nickname.takeIf { it.isNotBlank() } ?: " "
    }
}

data class Chapter(
    val id: String,
    val title: String,
    val content: String // Теперь здесь полноценный XHTML с тегами
)

/**
 * Хранит бинарные данные изображений, извлеченных из <binary>
 */
data class ImageData(
    val id: String,
    val contentType: String,
    val data: ByteArray
)

data class Book(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val authors: List<Author> = emptyList(),
    val language: String = "",
    val date: String = "",
    val coverImage: ByteArray? = null,
    val coverImageId: String? = null,
    val images: Map<String, ImageData> = emptyMap(), // Все изображения книги
    val chapters: List<Chapter> = emptyList(),
    val annotation: String = ""
) {
    fun authorsString(): String {
        return if (authors.isNotEmpty()) authors.joinToString(", ") { it.fullName() }
        else ""
    }
}