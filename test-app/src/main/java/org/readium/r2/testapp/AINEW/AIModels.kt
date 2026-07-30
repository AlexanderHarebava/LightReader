package org.readium.r2.testapp.AINEW

import com.google.gson.annotations.SerializedName

data class AIRequest(
    @SerializedName("model") val model: String = "deepseek/deepseek-chat",
    @SerializedName("messages") val messages: List<Message>,
    @SerializedName("temperature") val temperature: Double = 0.7,
    @SerializedName("max_tokens") val maxTokens: Int = 1000
) {
    data class Message(
        @SerializedName("role") val role: String,
        @SerializedName("content") val content: String
    )
}







data class EmbeddingResponse(
    @SerializedName("data") val data: List<EmbeddingData>?
) {
    data class EmbeddingData(
        @SerializedName("embedding") val embedding: List<Float>?
    )
}

data class AIResponse(
    @SerializedName("choices") val choices: List<Choice>?
) {
    data class Choice(
        @SerializedName("message") val message: AIRequest.Message?
    )




}


data class EmbeddingRequest(
    @SerializedName("model") val model: String,
    @SerializedName("input") val input: List<String>
)


data class GeminiEmbeddingRequest(
    @SerializedName("model") val model: String,
    @SerializedName("content") val content: GeminiContent
) {
    data class GeminiContent(
        @SerializedName("parts") val parts: List<Part>
    ) {
        data class Part(
            @SerializedName("text") val text: String
        )
    }
}



data class GeminiGenerateContentRequest(
    @SerializedName("contents") val contents: List<Content>,
    @SerializedName("systemInstruction") val systemInstruction: SystemInstruction? = null
) {
    data class Content(
        @SerializedName("role") val role: String, // "user" или "model"
        @SerializedName("parts") val parts: List<Part>
    )

    data class Part(
        @SerializedName("text") val text: String
    )

    data class SystemInstruction(
        @SerializedName("parts") val parts: List<Part>
    )
}

data class GeminiGenerateContentResponse(
    @SerializedName("candidates") val candidates: List<Candidate>?
) {
    data class Candidate(
        @SerializedName("content") val content: GeminiGenerateContentRequest.Content?
    )
}

data class GeminiEmbeddingResponse(
    @SerializedName("embedding") val embedding: EmbeddingData?
) {
    data class EmbeddingData(
        @SerializedName("values") val values: List<Float>?
    )
}
