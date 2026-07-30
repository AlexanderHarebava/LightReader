package org.readium.r2.testapp.ai

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.readium.r2.testapp.AINEW.AIManager
import org.readium.r2.testapp.AINEW.AIRequest
import org.readium.r2.testapp.AINEW.AiProviderType
import org.readium.r2.testapp.R

class CloudAiProvider(
    private val application: Application
) : AiProvider {


    private val aiManager: AIManager
        get() = (application as org.readium.r2.testapp.Application).aiManager

    private val _state = MutableStateFlow<ProviderState>(ProviderState.Idle)
    override val state: StateFlow<ProviderState> = _state.asStateFlow()

    override suspend fun initialize() {
        _state.value = ProviderState.Loading
        try {
            val app = application as org.readium.r2.testapp.Application
            val apiKey = app.getAiApiKey()
            if (apiKey.isNullOrBlank()) {
                _state.value = ProviderState.Error(
                    IllegalStateException(application.getString(R.string.ai_error_key_not_configured))
                )
                return
            }
            val providerType = AiProviderType.fromApiKey(apiKey)
            if (providerType == AiProviderType.UNKNOWN) {
                _state.value = ProviderState.Error(
                    IllegalStateException(application.getString(R.string.ai_error_unknown_key_format))
                )
                return
            }
            // ✅ Передаём тип провайдера
            aiManager.updateApiKey(
                apiKey = apiKey,
                providerType = providerType,
                customModel = app.getAiCustomModel(),
                customEmbeddingModel = app.getAiCustomEmbeddingModel()
            )
            _state.value = ProviderState.Ready
        } catch (e: Exception) {
            _state.value = ProviderState.Error(e)
        }
    }

    fun resetState() {
        _state.value = ProviderState.Idle
    }

    override fun generate(prompt: String, maxTokens: Int): Flow<String> = flow {
        val app = application as org.readium.r2.testapp.Application
        val latestKey = app.getAiApiKey()
        if (!latestKey.isNullOrBlank()) {
            val providerType = app.getAiProviderType()
            // ✅ Передаём тип провайдера
            aiManager.updateApiKey(
                apiKey = latestKey,
                providerType = providerType,
                customModel = app.getAiCustomModel(),
                customEmbeddingModel = app.getAiCustomEmbeddingModel()
            )
        }

        val messages = listOf(
            AIRequest.Message("system", "You're an AI in the book reader app"),
            AIRequest.Message("user", prompt)
        )
        val result = aiManager.processChat(messages)
        if (result.isSuccess) {
            val reply = result.getOrNull() ?: "Nothing"
            emit(reply)
        } else {
            val error = result.exceptionOrNull()
            emit("Error API: ${error?.message ?: "Unknown"}")
        }
    }.flowOn(Dispatchers.IO)
}