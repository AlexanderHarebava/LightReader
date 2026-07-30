// файл: test-app/src/main/java/org/readium/r2/testapp/ai/AiProvider.kt
package org.readium.r2.testapp.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AiProvider {
    val state: StateFlow<ProviderState>

    /**
     * Инициализация провайдера. Может быть длительной (например, загрузка модели).
     * После вызова состояние должно перейти в Ready или Error.
     */
    suspend fun initialize()

    /**
     * Генерирует ответ на основе текстового запроса.
     * Возвращает Flow токенов (в случае облачного API, пока что возвращаем один элемент).
     * @param prompt полный текст промпта
     * @param maxTokens максимальное количество генерируемых токенов
     */
    fun generate(prompt: String, maxTokens: Int = 512): Flow<String>
}

enum class AiMode {
    /** Облачное API (DeepSeek через OpenRouter) */
    CLOUD,
    /** Локальная модель (GGUF через llama) */
    LOCAL
}

/**
 * Универсальное состояние провайдера.
 */
sealed class ProviderState {
    /** Провайдер ещё не инициализирован */
    data object Idle : ProviderState()
    /** Идёт процесс инициализации (загрузка модели, проверка ключа) */
    data object Loading : ProviderState()
    /** Провайдер готов к работе */
    data object Ready : ProviderState()
    /** Произошла ошибка */
    data class Error(val throwable: Throwable) : ProviderState()
}