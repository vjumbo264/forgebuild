package com.forgebuild.taskflow.ai

import com.forgebuild.taskflow.settings.GeminiKeyStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Direct client for the Gemini generateContent API with function calling. Keys go to Google only. */
class GeminiClient(private val keyStore: GeminiKeyStore) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val client = HttpClient(OkHttp) {
        install(HttpTimeout) { requestTimeoutMillis = 60_000; connectTimeoutMillis = 15_000 }
        expectSuccess = false
    }

    private val model = "gemini-flash-lite-latest"
    private fun url() = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"

    class AllKeysFailedException : Exception("Every configured Gemini API key failed.")
    class NoKeysException : Exception("No Gemini API key configured.")

    /** Send the conversation; returns the parsed response JSON. Fails over across keys. */
    suspend fun generate(contents: JsonArray, tools: JsonArray?, systemInstruction: String): JsonObject {
        val keys = keyStore.keys.value
        if (keys.isEmpty()) throw NoKeysException()
        val body = buildJsonObject {
            putJsonObject("system_instruction") {
                putJsonArray("parts") { add(buildJsonObject { put("text", systemInstruction) }) }
            }
            put("contents", contents)
            if (tools != null) put("tools", tools)
            putJsonObject("generation_config") { put("temperature", 0.4); put("max_output_tokens", 2048) }
        }
        var lastError: Exception? = null
        for (key in keys) {
            try {
                val resp = client.post(url()) {
                    contentType(ContentType.Application.Json)
                    header("x-goog-api-key", key)
                    setBody(body.toString())
                }
                val text = resp.body<String>()
                if (resp.status.value in 200..299) {
                    return json.parseToJsonElement(text).jsonObject
                }
                // 4xx auth/quota/rate-limit, or 5xx -> try the next key
                lastError = Exception("HTTP ${resp.status.value}: $text")
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw AllKeysFailedException().also { it.initCause(lastError) }
    }

    companion object {
        fun textPart(t: String): JsonObject = buildJsonObject { put("text", t) }
        fun functionCallPart(name: String, args: JsonObject): JsonObject = buildJsonObject {
            putJsonObject("function_call") { put("name", name); put("args", args) }
        }
        fun functionResponsePart(name: String, result: String): JsonObject = buildJsonObject {
            putJsonObject("function_response") { put("name", name); putJsonObject("response") { put("result", JsonPrimitive(result)) } }
        }
        fun content(role: String, parts: List<JsonObject>): JsonObject = buildJsonObject {
            put("role", role); put("parts", JsonArray(parts))
        }
    }
}
