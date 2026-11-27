package com.forgebuild.taskflow.ai

import android.content.Context
import android.os.PowerManager
import com.forgebuild.taskflow.data.ChatMessage
import com.forgebuild.taskflow.data.Recurrence
import com.forgebuild.taskflow.data.TaskCategory
import com.forgebuild.taskflow.data.TaskCategoryHelper
import com.forgebuild.taskflow.data.TaskFlowDb
import com.forgebuild.taskflow.notify.NotificationHub
import com.forgebuild.taskflow.settings.GeminiKeyStore
import com.forgebuild.taskflow.util.CrashLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Runs the multi-turn Gemini function-calling loop and records the chat transcript in Room. */
typealias ChatMessage = com.forgebuild.taskflow.data.ChatMessage

class AgentOrchestrator private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TaskFlow:AIRequest")

    companion object {
        @Volatile
        private var instance: AgentOrchestrator? = null

        fun get(context: Context): AgentOrchestrator =
            instance ?: synchronized(this) {
                instance ?: AgentOrchestrator(context.applicationContext).also { instance = it }
            }

        private val INTERNAL_ERROR_PREFIXES = listOf(
            "No Gemini API key configured.",
            "Gemini servers are experiencing temporary high usage",
            "Configured Gemini API key is invalid or rejected",
            "Configured Gemini API keys failed.",
            "Request error:",
            "Error:"
        )
    }

    private val db = TaskFlowDb.get(appContext)
    private val chatDao = db.chatMessageDao()
    private val repo = com.forgebuild.taskflow.data.TaskRepository.get(context)
    private val client = GeminiClient(GeminiKeyStore.get(context))
    private val tools = TaskAgentTools(repo)

    /** Durable chat messages stream observed from Room. Survives app restarts. */
    val messages: StateFlow<List<ChatMessage>> = chatDao.observeAll()
        .stateIn(appScope, SharingStarted.Eagerly, emptyList())

    fun clearMessages(): Job = appScope.launch {
        chatDao.clearAll()
    }

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    fun submit(userText: String): Job = appScope.launch {
        send(userText)
    }

    fun submitAudio(mimeType: String, base64Audio: String): Job = appScope.launch {
        sendAudio(mimeType, base64Audio)
    }

    private suspend fun buildSystemInstruction(): String {
        val now = System.currentTimeMillis()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss (EEEE)", Locale.getDefault())
        val dateOnlyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateTimeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val tz = TimeZone.getDefault()
        val tzName = tz.getDisplayName(tz.inDaylightTime(Date(now)), TimeZone.LONG)
        val tzId = tz.id
        val remainingToday = repo.getRemainingMinutesToday()
        val allocatedToday = repo.getAllocatedMinutesToday()

        val activeTasks = repo.allActiveNow()
        val grouped = TaskCategoryHelper.groupByCategory(activeTasks)

        return buildString {
            append("You are TaskFlow's built-in intelligent task assistant. ")
            append("CURRENT DEVICE CLOCK & TIME ZONE: ")
            append(fmt.format(Date(now)))
            append(", Time zone: ").append(tzName).append(" (").append(tzId).append("). ")
            append("Today's schedule: ").append(allocatedToday).append(" minutes allocated, ")
            append(remainingToday).append(" minutes remaining unallocated before midnight. ")
            append("You manage the user's to-do list by calling the provided tools. ")

            append("EISENHOWER MATRIX CATEGORIES: Every task belongs to one of four canonical categories: ")
            append("(1) urgent_important = 'Important and urgent' (Urgent & Important; Do first — crises, immediate deadlines); ")
            append("(2) not_urgent_important = 'Important but not urgent' (Not Urgent & Important; Schedule — strategic goals, deep work, planning; DEFAULT when category is unspecified); ")
            append("(3) urgent_not_important = 'Unimportant and urgent' (Urgent & Unimportant; Delegate/minimize — interruptions, routine urgent requests); ")
            append("(4) not_urgent_not_important = 'Unimportant but not urgent' (Not Urgent & Unimportant; Eliminate/later — low-value backlog). ")
            append("Use category IDs ('urgent_important', 'not_urgent_important', 'urgent_not_important', 'not_urgent_not_important') when calling create_task, update_task, move_task_category, list_tasks, complete_task, or delete_task. ")
            append("When creating a task without an explicit category, infer the best Eisenhower category from context or default to 'not_urgent_important'. ")
            append("When asked to move or reclassify a task, call move_task_category (or update_task with category). ")
            append("When asked to summarize, prioritize, or triage tasks across the four quadrants, use summarize_matrix (or the current snapshot below) and provide clear, structured quadrant guidance. ")

            append("\n\nCURRENT ACTIVE TASKS BY CATEGORY:\n")
            TaskCategory.entries.forEach { cat ->
                val list = grouped[cat].orEmpty()
                append("- ${cat.label} [${cat.id}] (${list.size} active):\n")
                if (list.isEmpty()) {
                    append("  (none)\n")
                } else {
                    list.take(25).forEach { t ->
                        val sched = when {
                            t.fixedTime != null -> " @ ${dateTimeFmt.format(Date(t.fixedTime))}"
                            t.scheduledDate != null -> " on ${dateOnlyFmt.format(Date(t.scheduledDate))}"
                            else -> ""
                        }
                        val rec = if (t.recurrence != Recurrence.NONE) " [${t.recurrence}]" else ""
                        val parent = if (t.parentId != null) " [subtask of #${t.parentId}]" else ""
                        val notes = if (t.info.isNotBlank()) " — ${t.info.take(80)}" else ""
                        append("  • #${t.id} \"${t.title}\" (${t.durationMinutes}m)$sched$rec$parent$notes\n")
                    }
                    if (list.size > 25) {
                        append("  • ...and ${list.size - 25} more (call list_tasks for full list)\n")
                    }
                }
            }

            append("\nFUTURE TASKS & TIME BUDGETS: Each day has its own independent 1440-minute budget. For today, there are ")
            append(remainingToday).append(" minutes unallocated left before midnight. For tomorrow or any future date (next week, next month, etc.), the task uses THAT target date's budget (up to 1440m unallocated minus tasks on that date). NEVER check a future task against today's remaining time. Use scheduled_date (yyyy-MM-dd) for plain/normal tasks without a clock time, or fixed_time (yyyy-MM-dd HH:mm) for timed tasks. If a task does not fit on today, you can schedule it for tomorrow or a future date. ")
            append("FULL TASK CAPABILITIES: You have full access to create and manage every task type supported by TaskFlow: (1) untimed normal tasks for today or any future date; (2) fixed-time tasks for any datetime, including overnight/cross-midnight spans; (3) recurring tasks (daily, weekly with specific weekdays, monthly, yearly) with or without an expiration end date; (4) sub-tasks at any depth using parent_title or parent_id; (5) countdown timers (start, pause, extend); (6) all four Eisenhower Matrix categories. ")
            append("FIXED TIMES: A task must only get a fixed_time when the user explicitly specifies a time or clearly implies one (e.g. \"remind me at 3pm\", \"tomorrow at 9\"). NEVER auto-assign or invent a fixed time — when no time is requested, create the task with NO fixed_time, exactly as if the user added it manually without one. ")
            append("PROACTIVELY WRITE TASK INFO: When the user verbally or in text explains details, reasoning, steps, background context, contacts, locations, or specifics about a task, you MUST proactively populate the info field of create_task or update_task with those explanatory details. Strongly lean toward capturing this context so the user can reference it later, rather than creating a bare task title and discarding the specifics. ")
            append("SUB-TASK NESTING: When asked to create a task inside another task, or when adding sub-tasks, you MUST specify either parent_id (numeric ID) or parent_title (the title/name of the parent task, e.g. parent_title=Groceries). The system will automatically resolve the parent task by title or ID and nest the sub-task under it. Never create a sub-task as a top-level task when a parent was requested. ")
            append("TASK TIMERS: Every task has a countdown timer (based on its duration) that the user can play/start, pause, resume, and extend at any moment — including mid-countdown and after it has finished. Use start_timer, pause_timer and extend_timer (amount + unit minutes/hours) whenever the user asks. ")
            append("MANDATORY DURATION: Every task MUST have a duration in minutes (e.g. 15, 30, 45, 60). If the user does not specify a duration, you MUST estimate and assign a sensible duration yourself. Never leave duration blank. ")
            append("OPEN-ENDED INSTRUCTIONS: Understand vague requests like 'Set this to only happen on Tuesdays' (weekly, tue), or 'scatter three tasks across the week' (create 3 tasks on different days with reasonable durations). ")
            append("RECURRING TASKS may have an optional end date ('repeat every day until 2026-12-31'); set recurrence_end when the user gives an end condition, otherwise leave it unset (indefinite). ")
            append("VOICE INPUT: The user may send a raw audio voice note. Listen to the audio directly, transcribe it yourself, and act on the spoken request exactly as if it were typed. ")
            append("Use list_tasks, summarize_matrix, or get_day_status whenever needed. ")
            append("After completing actions, reply clearly and concisely summarizing what you did (or giving the requested summary/triage across the four categories).")
        }
    }

    private suspend fun historyContents(excludeMessageId: Long? = null): MutableList<JsonObject> {
        val recent = chatDao.getAll()
            .asSequence()
            .filter { it.id != excludeMessageId }
            .filter { it.role != ChatMessage.Role.ACTION }
            .filter { m ->
                m.role != ChatMessage.Role.MODEL ||
                    INTERNAL_ERROR_PREFIXES.none { prefix -> m.text.startsWith(prefix) }
            }
            .filter { it.text.isNotBlank() }
            .toList()
            .takeLast(12)
            .dropWhile { it.role != ChatMessage.Role.USER }
        val contents = mutableListOf<JsonObject>()
        recent.forEach { m ->
            contents.add(
                GeminiClient.content(
                    if (m.role == ChatMessage.Role.USER) "user" else "model",
                    listOf(GeminiClient.textPart(m.text))
                )
            )
        }
        return contents
    }

    /** Send one typed user instruction; immediately saved to Room and runs function-calling loop. */
    suspend fun send(userText: String) {
        if (_busy.value) return
        _busy.value = true
        chatDao.insert(ChatMessage(role = ChatMessage.Role.USER, text = userText))
        val contents = historyContents()
        val lastRole = contents.lastOrNull()?.get("role")?.jsonPrimitive?.contentOrNull
        if (contents.isEmpty() || lastRole != "user") {
            contents.add(GeminiClient.content("user", listOf(GeminiClient.textPart(userText))))
        }
        runLoop(contents)
    }

    /**
     * Send a RAW recorded voice note straight to Gemini (no on-device speech-to-text):
     * Immediately saved to Room and processed by Gemini.
     */
    suspend fun sendAudio(mimeType: String, base64Audio: String) {
        if (_busy.value) return
        _busy.value = true
        val insertedId = chatDao.insert(ChatMessage(role = ChatMessage.Role.USER, text = "Voice note (audio)"))
        val contents = historyContents(excludeMessageId = insertedId)
        contents.add(
            GeminiClient.content(
                "user",
                listOf(
                    GeminiClient.inlineDataPart(mimeType, base64Audio),
                    GeminiClient.textPart("This is a voice note the user just recorded. Listen to the audio, transcribe it yourself, then act on the spoken request (create/update/move/complete/list/summarize tasks as asked) exactly as if it were typed text.")
                )
            )
        )
        runLoop(contents)
    }

    private data class ParsedFnCall(val name: String, val args: JsonObject, val id: String?)

    private suspend fun runLoop(contents: MutableList<JsonObject>) {
        val actions = mutableListOf<String>()
        runCatching { wakeLock?.acquire(180_000L) }
        try {
            val systemInstruction = buildSystemInstruction()
            var rounds = 0
            while (rounds < 10) {
                rounds++
                val resp = client.generate(JsonArray(contents), tools.apiTools(), systemInstruction)
                val candidate = resp["candidates"]?.jsonArray?.firstOrNull()?.jsonObject ?: break
                val parts = candidate["content"]?.jsonObject?.get("parts")?.jsonArray ?: break
                val fnCalls = parts.mapNotNull { p ->
                    p.jsonObject["functionCall"]?.jsonObject?.let { fc ->
                        val name = fc["name"]?.jsonPrimitive?.contentOrNull ?: return@let null
                        val args = (fc["args"] as? JsonObject) ?: JsonObject(emptyMap())
                        val id = fc["id"]?.jsonPrimitive?.contentOrNull
                        ParsedFnCall(name, args, id)
                    }
                }
                if (fnCalls.isEmpty()) {
                    val text = parts.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }.joinToString("\n").trim()
                    if (text.isNotEmpty()) {
                        chatDao.insert(ChatMessage(role = ChatMessage.Role.MODEL, text = text))
                        if (actions.isNotEmpty()) {
                            NotificationHub.agentConfirmation(appContext, text)
                        }
                    }
                    break
                }
                contents.add(candidate["content"]!!.jsonObject)
                val responseParts = mutableListOf<JsonObject>()
                for (call in fnCalls) {
                    val result = tools.execute(call.name, call.args)
                    actions.add(result)
                    chatDao.insert(ChatMessage(role = ChatMessage.Role.ACTION, text = result))
                    responseParts.add(GeminiClient.functionResponsePart(call.name, result, call.id))
                }
                if (responseParts.isEmpty()) break
                contents.add(GeminiClient.content("user", responseParts))
            }
        } catch (e: GeminiClient.NoKeysException) {
            chatDao.insert(ChatMessage(role = ChatMessage.Role.MODEL,
                text = "No Gemini API key configured. Open Settings to add your key."))
        } catch (e: GeminiClient.HighUsageExhaustedException) {
            chatDao.insert(ChatMessage(role = ChatMessage.Role.MODEL,
                text = "Gemini servers are experiencing temporary high usage right now. The request was retried with backoff. Please try again in a few moments."))
        } catch (e: GeminiClient.InvalidApiKeyException) {
            CrashLogger.logHandledException(appContext, "AgentOrchestrator", "Gemini API key rejected", e)
            NotificationHub.apiKeysFailed(appContext)
            chatDao.insert(ChatMessage(role = ChatMessage.Role.MODEL,
                text = "Configured Gemini API key is invalid or rejected. Check Settings."))
        } catch (e: GeminiClient.BadRequestException) {
            CrashLogger.logHandledException(appContext, "AgentOrchestrator", "Gemini request error (HTTP 400)", e)
            chatDao.insert(ChatMessage(role = ChatMessage.Role.MODEL,
                text = "Request error: ${e.message ?: "Could not complete request."}"))
        } catch (e: GeminiClient.AllKeysFailedException) {
            CrashLogger.logHandledException(appContext, "AgentOrchestrator", "All Gemini API keys failed", e)
            NotificationHub.apiKeysFailed(appContext)
            chatDao.insert(ChatMessage(role = ChatMessage.Role.MODEL,
                text = "Configured Gemini API keys failed. Check Settings."))
        } catch (e: Exception) {
            CrashLogger.logHandledException(appContext, "AgentOrchestrator", "Unexpected AI error", e)
            chatDao.insert(ChatMessage(role = ChatMessage.Role.MODEL,
                text = "Error: ${e.message ?: "Could not complete request."}"))
        } finally {
            _busy.value = false
            runCatching {
                if (wakeLock?.isHeld == true) wakeLock.release()
            }
        }
    }
}
