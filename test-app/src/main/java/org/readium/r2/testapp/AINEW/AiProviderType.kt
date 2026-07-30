package org.readium.r2.testapp.AINEW

enum class AiProviderType {
    OPENROUTER,
    GOOGLE_GEMINI,
    OPENAI,
    DEEPSEEK,
    UNKNOWN;

    companion object {
        fun fromApiKey(apiKey: String): AiProviderType {
            val trimmed = apiKey.trim()
            return when {
                trimmed.startsWith("sk-or-v1-") -> OPENROUTER
                trimmed.startsWith("AIza") && trimmed.length >= 35 -> GOOGLE_GEMINI
                trimmed.startsWith("sk-") && trimmed.length == 35 && trimmed.matches(Regex("^sk-[a-f0-9]{32}$", RegexOption.IGNORE_CASE)) -> DEEPSEEK
                trimmed.startsWith("sk-") -> OPENAI
                else -> UNKNOWN
            }
        }

        fun AiProviderType.displayName(): String = when (this) {
            OPENROUTER -> "OpenRouter"
            GOOGLE_GEMINI -> "Google Gemini"
            OPENAI -> "OpenAI"
            DEEPSEEK -> "DeepSeek"
            UNKNOWN -> "unknown"
        }

        fun AiProviderType.baseUrl(): String = when (this) {
            OPENROUTER -> "https://openrouter.ai/api/v1/"
            GOOGLE_GEMINI -> "https://generativelanguage.googleapis.com/v1beta/"
            OPENAI -> "https://api.openai.com/v1/"
            DEEPSEEK -> "https://api.deepseek.com/v1/"
            UNKNOWN -> ""
        }

        fun AiProviderType.defaultModel(): String = when (this) {
            OPENROUTER -> "deepseek/deepseek-chat"
            GOOGLE_GEMINI -> "gemini-3.6-flash"
            OPENAI -> "gpt-4o-mini"
            DEEPSEEK -> "deepseek-chat"
            UNKNOWN -> ""
        }


        fun AiProviderType.embeddingModel(): String = when (this) {
            OPENROUTER -> "openai/text-embedding-3-small"
            GOOGLE_GEMINI -> "gemini-embedding-001"
            OPENAI -> "text-embedding-3-small"
            DEEPSEEK -> "deepseek-embedding"
            UNKNOWN -> ""
        }
    }

}