package com.forgebuild.taskflow.ai

import android.content.Context
import com.forgebuild.taskflow.notify.NotificationHub
import com.forgebuild.taskflow.settings.GeminiKeyStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChatMessage(val role: Role, val text: String) {
    enum class Role { USER, MODEL, ACTION }
}

/** Runs the multi-turn Gemini function-calling loop and records the chat transcript. */
class AgentOrchestrator(context: Context) {
    private val appContext = context.applicationContext
    private val client = GeminiClient(GeminiKeyStore.get(context))
    private val tools = TaskAgentTools(com.forgebuild.taskflow.data.TaskRepository.get(context))

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val systemInstruction = buildString {
        append("You are TaskFlow's built-in task assistant. Current local time: ")
        append(SimpleDateFormat("yyyy-MM-dd HH:mm (EEEE)", Locale.getDefault()).format(Date()))
        append(". You manage the user's to-do list by calling the provided functions. ")
        append("Tasks are priority-sorted and support: optional fixed due time, recurrence (none/daily/weekly/monthly/yearly), free-text info, and unlimited nested sub-tasks. ")
        append("When the user's instruction is vague, infer sensible defaults and act WITHOUT asking for every detail — e.g. for 'scatter three tasks across the week', create three tasks on different days with reasonable priorities. ")
        append("Use list_tasks first when you need to resolve which task the user means or to place something between two named tasks. ")
        append("You may chain multiple calls. After finishing, reply with one concise natural-language sentence summarizing exactly what you did.")
    }

    /** Send one user instruction; runs the function-calling loop to completion. */
    suspend fun send(userText: String) {
        if (_busy.value) return
        _busy.value = true
        _messages.value = _messages.value + ChatMessage(ChatMessage.Role.USER, userText)
        val contents = mutableListOf<JsonObject>()
        // replay brief history (user/model text only)
        _messages.value.takeLast(12).filter { it.role != ChatMessage.Role.ACTION }.forEach { m ->
            contents.add(GeminiClient.content(if (m.role == ChatMessage.Role.USER) "user" else "model",
                listOf(GeminiClient.textPart(m.text))))
        }
        val actions = mutableListOf<String>()
        try {
            var rounds = 0
            while (rounds < 10) {
                rounds++
                val resp = client.generate(JsonArray(contents), tools.apiTools(), systemInstruction)
                val candidate = resp["candidates"]?.jsonArray?.firstOrNull()?.jsonObject ?: break
                val parts = candidate["content"]?.jsonObject?.get("parts")?.jsonArray ?: break
                val calls = parts.mapNotNull { it.jsonObject["functionCall"]?.jsonObject }
                val texts = parts.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
                if (calls.isEmpty()) {
                    val reply = texts.joinToString("\n").ifBlank { "Done." }
                    _messages.value = _messages.value + ChatMessage(ChatMessage.Role.MODEL, reply)
                    if (actions.isNotEmpty()) NotificationHub.agentConfirmation(appContext, actions.joinToString(" · "))
                    break
                }
                // record model's content turn then execute every call
                contents.add(GeminiClient.content("model", parts.map { it.jsonObject }))
                val responseParts = mutableListOf<JsonObject>()
                for (call in calls) {
                    val name = call["name"]?.jsonPrimitive?.content ?: continue
                    val args = call["args"]?.jsonObject ?: JsonObject(emptyMap())
                    val result = runCatching { tools.execute(name, args) }.getOrElse { "Error: ${it.message}" }
                    actions += result
                    _messages.value = _messages.value + ChatMessage(ChatMessage.Role.ACTION, result)
                    responseParts.add(GeminiClient.functionResponsePart(name, result))
                }
                contents.add(GeminiClient.content("user", responseParts))
            }
        } catch (e: GeminiClient.NoKeysException) {
            _messages.value = _messages.value + ChatMessage(ChatMessage.Role.MODEL,
                "No Gemini API key is set. Add one in Settings > Gemini API keys.")
        } catch (e: GeminiClient.AllKeysFailedException) {
            _messages.value = _messages.value + ChatMessage(ChatMessage.Role.MODEL,
                "Every Gemini API key failed. Please check your keys in Settings.")
            NotificationHub.apiKeysFailed(appContext)
        } catch (e: Exception) {
            _messages.value = _messages.value + ChatMessage(ChatMessage.Role.MODEL,
                "Something went wrong: ${e.message ?: "unknown error"}")
        } finally { _busy.value = false }
    }
}
