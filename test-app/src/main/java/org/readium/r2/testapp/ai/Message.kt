package org.readium.r2.testapp.ai

data class Message(
    val text: String,
    val isFromUser: Boolean,
    val bookTitle: String? = null,
    val bookCoverPath: String? = null,
    val bookId: Long? = null
)