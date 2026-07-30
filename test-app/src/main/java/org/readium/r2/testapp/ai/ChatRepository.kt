package org.readium.r2.testapp.ai

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Хранилище чатов: один JSON-файл на чат в filesDir/ai_chats/.
 * Список всегда держим в памяти (StateFlow), запись на диск — асинхронно.
 */
class ChatRepository(private val context: Context) {

    private val dir = File(context.filesDir, "ai_chats")
    private val gson = Gson()
    private val mutex = Mutex()

    private val _chats = MutableStateFlow<List<ChatSession>>(emptyList())
    val chats: StateFlow<List<ChatSession>> = _chats.asStateFlow()

    private var loaded = false

    /** Загрузка всех чатов + миграция со старого формата (один общий "history"). */
    suspend fun loadAll() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (loaded) return@withContext
            dir.mkdirs()

            val type = object : TypeToken<ChatSession>() {}.type
            val list = dir.listFiles { f -> f.extension == "json" }
                ?.mapNotNull { file ->
                    runCatching { gson.fromJson<ChatSession>(file.readText(), type) }.getOrNull()
                }
                ?.sortedByDescending { it.updatedAt }
                ?: emptyList()

            _chats.value = list
            migrateLegacyHistory()
            loaded = true
        }
    }

    /** Переносит старую единую историю из SharedPreferences в отдельный чат. */
    private fun migrateLegacyHistory() {
        val prefs = context.getSharedPreferences("ai_chat_prefs", 0)
        val json = prefs.getString("history", null) ?: return
        val type = object : TypeToken<MutableList<Message>>() {}.type
        val messages = runCatching {
            gson.fromJson<MutableList<Message>>(json, type)
        }.getOrNull()
        prefs.edit().remove("history").apply()

        if (messages.isNullOrEmpty()) return
        val now = System.currentTimeMillis()
        val legacy = ChatSession(
            id = UUID.randomUUID().toString(),
            title = "Previous chat",
            createdAt = now,
            updatedAt = now,
            messages = messages
        )
        writeToFile(legacy)
        _chats.value = (_chats.value + legacy).sortedByDescending { it.updatedAt }
    }

    fun getChat(id: String): ChatSession? = _chats.value.firstOrNull { it.id == id }

    suspend fun createChat(title: String): ChatSession = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val chat = ChatSession(UUID.randomUUID().toString(), title, now, now)
            writeToFile(chat)
            _chats.value = (_chats.value + chat).sortedByDescending { it.updatedAt }
            chat
        }
    }

    suspend fun renameChat(id: String, newTitle: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            getChat(id)?.let { chat ->
                chat.title = newTitle.trim()
                writeToFile(chat)
                _chats.value = ArrayList(_chats.value) // триггер эмиссии
            }
        }
    }

    suspend fun saveMessages(id: String, messages: List<Message>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            getChat(id)?.let { chat ->
                chat.messages.clear()
                chat.messages.addAll(messages)
                chat.updatedAt = System.currentTimeMillis()
                writeToFile(chat)
                _chats.value = _chats.value.sortedByDescending { it.updatedAt }
            }
        }
    }


    fun buildPlainText(chat: ChatSession): String = buildString {
        append(chat.title).append('\n')
        append("=".repeat(32)).append("\n\n")
        chat.messages.forEach { m ->
            append(if (m.isFromUser) "You" else "AI").append(":\n")
            append(m.text.trim()).append("\n\n")
        }
    }

    suspend fun deleteChat(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            File(dir, "$id.json").delete()
            _chats.value = _chats.value.filterNot { it.id == id }
        }
    }

    /** Текст чата в формате Markdown — для скачивания и «Поделиться». */
    fun buildMarkdown(chat: ChatSession): String = buildString {
        append("# ").append(chat.title).append("\n\n")
        chat.messages.forEach { m ->
            append(if (m.isFromUser) "**You:** " else "**AI:** ")
            append(m.text).append("\n\n---\n\n")
        }
    }

    private fun writeToFile(chat: ChatSession) {
        File(dir, "${chat.id}.json").writeText(gson.toJson(chat))
    }
}