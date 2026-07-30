package org.readium.r2.testapp.AINEW

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface AIService {
    @POST("chat/completions")
    suspend fun processChat(
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: AIRequest
    ): Response<AIResponse>


    @POST("embeddings")
    suspend fun getEmbeddings(
        @Header("Authorization") authorization: String,
        @Body request: EmbeddingRequest
    ): Response<EmbeddingResponse>


    @POST("models/{model}:embedContent")
    suspend fun getGeminiEmbeddings(
        @Header("x-goog-api-key") apiKey: String,
        @Path("model") model: String,
        @Body request: GeminiEmbeddingRequest
    ): Response<GeminiEmbeddingResponse>

    @POST("models/{model}:generateContent")
    suspend fun processGeminiChat(
        @Header("x-goog-api-key") apiKey: String,
        @Path("model") model: String,
        @Body request: GeminiGenerateContentRequest
    ): Response<GeminiGenerateContentResponse>

}