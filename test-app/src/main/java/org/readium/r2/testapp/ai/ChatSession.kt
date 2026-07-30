package org.readium.r2.testapp.ai


data class ChatSession(
    val id: String,
    var title: String,
    val createdAt: Long,
    var updatedAt: Long,
    val messages: MutableList<Message> = mutableListOf()
)