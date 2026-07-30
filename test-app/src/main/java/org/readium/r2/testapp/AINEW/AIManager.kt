package org.readium.r2.testapp.AINEW

import android.R.attr.text
import android.content.Context
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content


import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.readium.r2.testapp.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import org.readium.r2.testapp.AINEW.AiProviderType.Companion.baseUrl
import org.readium.r2.testapp.AINEW.AiProviderType.Companion.defaultModel
import org.readium.r2.testapp.AINEW.AiProviderType.Companion.embeddingModel
import org.readium.r2.testapp.R

import timber.log.Timber

class AIManager(private val context: Context) {
    private var currentApiKey: String? = null
    private var currentProviderType: AiProviderType = AiProviderType.UNKNOWN
    private var currentModel: String = ""
    private var currentEmbeddingModel: String = ""
    private lateinit var aiService: AIService
    private val keyMutex = Mutex()

    init {
        initRetrofit(AiProviderType.OPENROUTER.baseUrl())
    }

    suspend fun updateApiKey(
        apiKey: String,
        providerType: AiProviderType? = null,
        customModel: String? = null,
        customEmbeddingModel: String? = null
    ) = withContext(Dispatchers.IO) {
        keyMutex.withLock {
            val normalizedKey = apiKey.trim()

            val newProvider = if (normalizedKey.isBlank()) {
                AiProviderType.UNKNOWN
            } else {
                providerType ?: AiProviderType.fromApiKey(normalizedKey)
            }

            currentApiKey = normalizedKey.takeIf { it.isNotBlank() }
            currentProviderType = newProvider

            currentModel = customModel
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: newProvider.defaultModel()

            currentEmbeddingModel = customEmbeddingModel
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: newProvider.embeddingModel()

            val baseUrl = newProvider.baseUrl()

            if (baseUrl.isNotBlank()) {
                initRetrofit(baseUrl)
            }
        }
    }

    suspend fun getCurrentApiKey(): String? = keyMutex.withLock { currentApiKey }
    suspend fun getCurrentModel(): String = keyMutex.withLock { currentModel }

    private fun initRetrofit(baseUrl: String) {
        val logging = HttpLoggingInterceptor().apply {
            level =
                if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        aiService = retrofit.create(AIService::class.java)
    }

    suspend fun getEmbeddingsBatch(texts: List<String>): List<FloatArray>? =
        withContext(Dispatchers.IO) {
            if (currentApiKey.isNullOrBlank() || texts.isEmpty()) return@withContext null

            try {
                return@withContext when (currentProviderType) {
                    AiProviderType.GOOGLE_GEMINI -> getGeminiEmbeddings(texts)
                    else -> getOpenAIStyleEmbeddings(texts) // OpenAI, OpenRouter, DeepSeek
                }
            } catch (e: Exception) {
                Timber.e("Ошибка соединения при получении эмбеддинга: ${e.localizedMessage}")
                return@withContext null
            }
        }

    // ✅ Для OpenAI/OpenRouter/DeepSeek
    private suspend fun getOpenAIStyleEmbeddings(texts: List<String>): List<FloatArray>? {
        val request = EmbeddingRequest(
            model = currentEmbeddingModel,
            input = texts
        )
        val response = aiService.getEmbeddings("Bearer $currentApiKey", request)

        if (response.isSuccessful && response.body() != null) {
            val dataList = response.body()?.data ?: return null
            return dataList.mapNotNull { it.embedding?.toFloatArray() }
        } else {
            val errorBody = response.errorBody()?.string() ?: "Error"
            Timber.e("Ошибка Embeddings API: ${response.code()}\n$errorBody")
            return null
        }
    }


    private suspend fun getGeminiEmbeddings(texts: List<String>): List<FloatArray>? {
        val results = mutableListOf<FloatArray>()

        for (text in texts) {
            var retryCount = 0
            var success = false
            val maxRetries = 3

            while (!success && retryCount < maxRetries) {
                val request = GeminiEmbeddingRequest(
                    model = currentEmbeddingModel,
                    content = GeminiEmbeddingRequest.GeminiContent(
                        parts = listOf(GeminiEmbeddingRequest.GeminiContent.Part(text = text))
                    )
                )

                try {
                    val response = aiService.getGeminiEmbeddings(
                        apiKey = currentApiKey!!,
                        model = currentEmbeddingModel,
                        request = request
                    )

                    if (response.isSuccessful && response.body() != null) {
                        val embedding = response.body()?.embedding?.values?.toFloatArray()
                        if (embedding != null) {
                            results.add(embedding)
                            success = true

                            kotlinx.coroutines.delay(250)
                        } else {
                            Timber.e("Пустой эмбеддинг от Google Gemini для текста")
                            return null
                        }
                    } else {
                        val code = response.code()
                        val errorBody = response.errorBody()?.string() ?: "Error"


                        if (code == 503 || code == 429) {
                            retryCount++
                            Timber.w("Gemini API перегружен (ошибка $code). Попытка $retryCount из $maxRetries. Ждем...")

                            kotlinx.coroutines.delay((2000L * retryCount))
                        } else {
                            Timber.e("Ошибка Google Gemini Embeddings API: $code\n$errorBody")
                            return null
                        }
                    }
                } catch (e: Exception) {
                    Timber.e("Сетевая ошибка при запросе Gemini: ${e.message}")
                    retryCount++
                    kotlinx.coroutines.delay(2000L)
                }
            }

            if (!success) {
                Timber.e("Не удалось получить эмбеддинг после $maxRetries попыток.")
                return null
            }
        }

        return results
    }

    suspend fun getEmbedding(text: String): FloatArray? {
        return getEmbeddingsBatch(listOf(text))?.firstOrNull()

    }



    suspend fun processChat(messages: List<AIRequest.Message>): Result<String> =
        withContext(Dispatchers.IO) {
            if (currentApiKey.isNullOrBlank()) {
                return@withContext Result.failure(Exception(context.getString(R.string.ai_error_key_not_set)))
            }

            try {
                // 🔥 РАЗВИЛКА: Если это Google Gemini, используем Firebase SDK
                if (currentProviderType == AiProviderType.GOOGLE_GEMINI) {


                    val generativeModel = GenerativeModel(
                        modelName = currentModel, // "gemini-1.5-flash"
                        apiKey = currentApiKey!!
                    )

                    val history = messages.dropLast(1).map { msg ->
                        content(role = if (msg.role == "assistant") "model" else "user") {
                            text(msg.content)
                        }
                    }

                    val chat = generativeModel.startChat(history = history)
                    val lastMessage = messages.last().content
                    val response = chat.sendMessage(lastMessage)



                    val reply = response.text
                    if (reply != null) {
                        return@withContext Result.success(reply)
                    } else {
                        return@withContext Result.failure(Exception(context.getString(R.string.ai_error_empty_response_gemini)))
                    }

                }
                // 🌐 РАЗВИЛКА: Для OpenAI, OpenRouter, DeepSeek используем Retrofit
                else {
                    val request = AIRequest(
                        model = currentModel,
                        messages = messages
                    )

                    val response = aiService.processChat("Bearer $currentApiKey", request = request)
                    if (response.isSuccessful && response.body() != null) {
                        val content = response.body()?.choices?.firstOrNull()?.message?.content
                        if (content != null) {
                            return@withContext Result.success(content)
                        } else {
                            return@withContext Result.failure(Exception(context.getString(R.string.ai_error_empty_response)))
                        }
                    } else {
                        val errorBody = response.errorBody()?.string() ?: context.getString(R.string.ai_error_unknown_error)
                        return@withContext Result.failure(Exception(
                            context.getString(R.string.ai_error_api_with_code, response.code(), response.message(), errorBody)
                        ))       }
                }
            } catch (e: Exception) {
                return@withContext Result.failure(Exception(
                    context.getString(R.string.ai_error_connection, e.localizedMessage)
                ))
            }
        }

}
