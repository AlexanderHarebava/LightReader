package org.readium.r2.testapp.ai

import android.app.Application
import androidx.datastore.core.Closeable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.readium.r2.testapp.AINEW.AIManager
import org.readium.r2.testapp.AINEW.AIRequest
import org.readium.r2.testapp.AINEW.AiProviderType
import org.readium.r2.testapp.R
import org.readium.r2.testapp.data.model.Book
import timber.log.Timber

class AiChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<org.readium.r2.testapp.Application>()
    private val chatRepository get() = app.chatRepository

    private val systemPrompt: String by lazy {
        app.getString(R.string.ai_system_prompt_book_reader)
    }
    private val defaultChatTitle: String by lazy {
        app.getString(R.string.chat_default_title)
    }

    private val _messages = MutableLiveData<MutableList<Message>>(mutableListOf())
    val messages: LiveData<MutableList<Message>> = _messages

    private val _chatList = MutableLiveData<List<ChatSession>>(emptyList())
    val chatList: LiveData<List<ChatSession>> = _chatList

    private val _currentChat = MutableLiveData<ChatSession?>(null)
    val currentChat: LiveData<ChatSession?> = _currentChat

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private var activeJob: Job? = null
    private var currentChatId: String? = null

    init {
        viewModelScope.launch {
            chatRepository.loadAll()
            val chats = chatRepository.chats.value
            if (chats.isEmpty()) {
                openChat(chatRepository.createChat(defaultChatTitle).id)
            } else {
                openChat(chats.first().id) // самый свежий чат
            }
            chatRepository.chats.collect { _chatList.value = it }
        }
    }

    // ================= Управление чатами =================

    fun openChat(chatId: String) {
        val chat = chatRepository.getChat(chatId) ?: return
        if (chatId == currentChatId) return
        activeJob?.cancel() // как на ИИ-сайтах: смена чата прерывает генерацию
        currentChatId = chatId
        _messages.value = chat.messages.toMutableList()
        _currentChat.value = chat
    }

    fun startNewChat() {
        viewModelScope.launch {
            val chat = chatRepository.createChat(defaultChatTitle)
            openChat(chat.id)
        }
    }

    fun renameChat(chatId: String, newTitle: String) {
        viewModelScope.launch {
            chatRepository.renameChat(chatId, newTitle)
            refreshCurrentChat()
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            chatRepository.deleteChat(chatId)
            if (currentChatId == chatId) {
                currentChatId = null
                val rest = chatRepository.chats.value
                if (rest.isEmpty()) {
                    openChat(chatRepository.createChat(defaultChatTitle).id)
                } else {
                    openChat(rest.first().id)
                }
            }
        }
    }


    fun getChatExportText(chatId: String): String =
        chatRepository.getChat(chatId)?.let { chatRepository.buildPlainText(it) }.orEmpty()

    private fun refreshCurrentChat() {
        currentChatId?.let { _currentChat.value = chatRepository.getChat(it) }
    }

    fun cancelActiveOperation() {
        activeJob?.cancel()
        _isLoading.value = false
    }

    // ================= Сообщения =================

    fun addMessage(message: Message) {
        val currentList = _messages.value ?: mutableListOf()
        currentList.add(message)
        _messages.value = currentList

        val chatId = currentChatId ?: return
        viewModelScope.launch {
            // Автотайтл: первое пользовательское сообщение становится названием чата
            if (message.isFromUser) {
                val chat = chatRepository.getChat(chatId)
                if (chat != null && chat.title == defaultChatTitle && message.text.isNotBlank()) {
                    val base = message.text.trim().replace('\n', ' ')
                    val title = if (base.length > 40) base.take(40).trim() + "…" else base
                    chatRepository.renameChat(chatId, title)
                }
            }
            chatRepository.saveMessages(chatId, currentList)
            refreshCurrentChat()
        }
    }

    fun clearMessages() {
        _messages.value = mutableListOf()
        val chatId = currentChatId ?: return
        viewModelScope.launch { chatRepository.saveMessages(chatId, emptyList()) }
    }

    // ================= RAG (без изменений) =================

    data class TextChunk(
        val id: String,
        val text: String,
        var embedding: FloatArray? = null
    )

    fun chunkText(text: String, chunkSize: Int = 1500, overlap: Int = 200): List<String> {
        val chunks = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            val end = (i + chunkSize).coerceAtMost(text.length)
            chunks.add(text.substring(i, end))
            i += (chunkSize - overlap)
        }
        return chunks
    }

    fun cosineSimilarity(vectorA: FloatArray, vectorB: FloatArray): Float {
        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f
        for (i in vectorA.indices) {
            dotProduct += vectorA[i] * vectorB[i]
            normA += vectorA[i] * vectorA[i]
            normB += vectorB[i] * vectorB[i]
        }
        return (dotProduct / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)))
    }

    private val bookChunksCache = mutableMapOf<Long, List<TextChunk>>()

    private suspend fun getCachedBookChunks(book: Book, aiManager: AIManager): List<TextChunk> {
        val bookId = book.id ?: return emptyList()
        if (bookChunksCache.containsKey(bookId)) {
            return bookChunksCache[bookId]!!
        }

        val readium = app.readium
        var fullText = ""
        try {
            val asset = readium.assetRetriever.retrieve(book.url, book.mediaType).getOrNull()
            if (asset != null) {
                val publication = readium.publicationOpener.open(asset, allowUserInteraction = false).getOrNull()
                if (publication != null) {
                    val textBuilder = StringBuilder()
                    for (link in publication.readingOrder) {
                        val resource = publication.get(link)
                        val content = resource?.read()?.getOrNull()?.let { String(it) } ?: ""
                        val cleanText = content.replace(Regex("<[^>]*>"), "")
                        textBuilder.append(cleanText).append("\n")
                        resource?.close()
                    }
                    fullText = textBuilder.toString()
                    publication.close()
                }
                (asset as? Closeable)?.close()
            }
        } catch (e: Exception) {
            Timber.e(e, app.getString(R.string.ai_log_rag_extraction_error))
            return emptyList()
        }

        if (fullText.isEmpty()) return emptyList()

        val rawChunks = chunkText(fullText, chunkSize = 1500, overlap = 200)
        val textChunks = mutableListOf<TextChunk>()
        val batchSize = 300
        val batches = rawChunks.chunked(batchSize)
        val allEmbeddings = mutableListOf<List<FloatArray>?>()

        for (batch in batches) {
            yield()
            val embeddings = aiManager.getEmbeddingsBatch(batch)
            allEmbeddings.add(embeddings)
            kotlinx.coroutines.delay(1000)
        }

        batches.forEachIndexed { batchIndex, batch ->
            val embeddings = allEmbeddings[batchIndex]
            if (embeddings != null && embeddings.size == batch.size) {
                batch.forEachIndexed { i, text ->
                    val globalIndex = batchIndex * batchSize + i
                    textChunks.add(
                        TextChunk(
                            id = "${bookId}_$globalIndex",
                            text = text,
                            embedding = embeddings[i]
                        )
                    )
                }
            } else {
                Timber.e(app.getString(R.string.ai_log_failed_get_embeddings, batchIndex))
            }
        }

        bookChunksCache[bookId] = textChunks
        return textChunks
    }

    fun prepareBookContext(book: Book, aiManager: AIManager) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            withContext(Dispatchers.Main) { _isLoading.value = true }
            try {
                withContext(Dispatchers.IO) {
                    val providerType = app.getAiProviderType()
                    if (providerType == AiProviderType.GOOGLE_GEMINI) {
                        Timber.d(app.getString(R.string.ai_log_gemini_skip_rag))
                    } else {
                        getCachedBookChunks(book, aiManager)
                    }
                }
            } catch (e: CancellationException) {
                Timber.d("AI preparation canceled")
            } catch (e: Exception) {
                Timber.e(e)
            } finally {
                withContext(NonCancellable + Dispatchers.Main) { _isLoading.value = false }
            }
        }
    }

    private suspend fun getFullBookText(book: Book): String {
        val readium = app.readium
        var fullText = ""
        try {
            val asset = readium.assetRetriever.retrieve(book.url, book.mediaType).getOrNull()
            if (asset != null) {
                val publication = readium.publicationOpener.open(asset, allowUserInteraction = false).getOrNull()
                if (publication != null) {
                    val textBuilder = StringBuilder()
                    for (link in publication.readingOrder) {
                        yield()
                        val resource = publication.get(link)
                        val content = resource?.read()?.getOrNull()?.let { String(it) } ?: ""
                        val cleanText = content.replace(Regex("<[^>]*>"), "")
                        textBuilder.append(cleanText).append("\n")
                        resource?.close()
                    }
                    fullText = textBuilder.toString()
                    publication.close()
                }
                (asset as? Closeable)?.close()
            }
        } catch (e: Exception) {
            Timber.e(e, app.getString(R.string.ai_log_gemini_extraction_error))
        }
        return fullText
    }

    fun sendMessageToAI(userText: String, aiManager: AIManager, selectedBook: Book?) {
        val displayMessage = if (userText.isEmpty()) {
            app.getString(R.string.ai_analyze_book_default)
        } else {
            userText
        }

        addMessage(
            Message(
                text = displayMessage,
                isFromUser = true,
                bookTitle = selectedBook?.title,
                bookCoverPath = selectedBook?.cover,
                bookId = selectedBook?.id
            )
        )

        val history = (_messages.value ?: mutableListOf())
            .dropLast(1)
            .map { it.copy() }

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            withContext(Dispatchers.Main) { _isLoading.value = true }
            try {
                val reply = withContext(Dispatchers.IO) {
                    var relevantContext = ""
                    if (selectedBook != null) {
                        val providerType = app.getAiProviderType()
                        if (providerType == AiProviderType.GOOGLE_GEMINI) {
                            relevantContext = getFullBookText(selectedBook)
                        } else {
                            val questionEmbedding = aiManager.getEmbedding(displayMessage)
                            if (questionEmbedding != null) {
                                val bookChunks: List<TextChunk> = getCachedBookChunks(selectedBook, aiManager)
                                val topChunks = bookChunks.mapNotNull { chunk ->
                                    val embedding = chunk.embedding ?: return@mapNotNull null
                                    Triple(chunk, embedding, cosineSimilarity(questionEmbedding, embedding))
                                }
                                    .sortedByDescending { it.third }
                                    .take(3)
                                relevantContext = topChunks.joinToString("\n...\n") { it.first.text }
                            } else {
                                throw Exception(app.getString(R.string.ai_error_vector_creation))
                            }
                        }
                    }

                    val apiMessages = mutableListOf<AIRequest.Message>()
                    apiMessages.add(AIRequest.Message(role = "system", content = systemPrompt))
                    history.forEach { msg ->
                        val role = if (msg.isFromUser) "user" else "assistant"
                        apiMessages.add(AIRequest.Message(role = role, content = msg.text))
                    }

                    val finalPrompt = if (relevantContext.isNotEmpty()) {
                        app.getString(R.string.ai_prompt_context, relevantContext, displayMessage)
                    } else {
                        displayMessage
                    }
                    apiMessages.add(AIRequest.Message(role = "user", content = finalPrompt))

                    aiManager.processChat(apiMessages).getOrThrow()
                }

                withContext(Dispatchers.Main) {
                    addMessage(Message(text = reply, isFromUser = false))
                }
            } catch (e: CancellationException) {
                Timber.d("AI request canceled")
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    addMessage(Message(text = "Error: ${e.message}", isFromUser = false))
                }
            } finally {
                withContext(NonCancellable + Dispatchers.Main) { _isLoading.value = false }
            }
        }
    }
}