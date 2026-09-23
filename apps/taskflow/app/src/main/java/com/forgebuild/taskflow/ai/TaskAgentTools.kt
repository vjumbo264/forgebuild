package com.forgebuild.taskflow.ai

import com.forgebuild.taskflow.data.Recurrence
import com.forgebuild.taskflow.data.Task
import com.forgebuild.taskflow.data.TaskRepository
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Declares Gemini function-calling schemas and executes them against the local Room DB.
 * Guarantees time zone/clock awareness, mandatory duration inference, and remaining time validation.
 */
class TaskAgentTools(private val repo: TaskRepository) {

    private fun prop(type: String, desc: String): JsonObject = buildJsonObject {
        put("type", type)
        put("description", desc)
    }

    /** Real tools array for Gemini generateContent API. */
    fun apiTools(): JsonArray {
        val decls = mutableListOf<JsonObject>()
        fun d(name: String, desc: String, props: Map<String, JsonObject>, required: List<String> = emptyList()) {
            decls.add(buildJsonObject {
                put("name", name)
                put("description", desc)
                putJsonObject("parameters") {
                    put("type", "object")
                    putJsonObject("properties") { props.forEach { (k, v) -> put(k, v) } }
                    if (required.isNotEmpty()) putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
                }
            })
        }

        d("get_day_status", "Get current device local time, time zone, and remaining unallocated minutes left today.", emptyMap())

        d("list_tasks", "List tasks. Use parent_id for sub-tasks, omit for top level. Filter by optional query.", mapOf(
            "parent_id" to prop("integer", "Parent task id; omit for top level."),
            "query" to prop("string", "Optional case-insensitive title filter.")))

        d("create_task", "Create a task. Duration is mandatory (in minutes); if user did not specify, you MUST infer a reasonable estimate (e.g. 15, 30, 45, 60 min).", mapOf(
            "title" to prop("string", "Task title (required)."),
            "duration_minutes" to prop("integer", "Estimated duration in minutes (e.g. 15, 30, 45, 60). Mandatory."),
            "parent_id" to prop("integer", "Parent id for a sub-task."),
            "fixed_time" to prop("string", "Local datetime 'yyyy-MM-dd HH:mm' if a specific due time applies."),
            "recurrence" to prop("string", "none, daily, weekly, monthly, yearly."),
            "weekdays" to prop("string", "For weekly: comma list like tue or mon,wed,fri."),
            "recurrence_end" to prop("string", "Optional last day of a recurrence as 'yyyy-MM-dd' (repeat until that date, then stop). Omit for indefinite."),
            "info" to prop("string", "Free-text notes on how to do the task.")), listOf("title"))

        d("update_task", "Update any task field.", mapOf(
            "task_id" to prop("integer", "Task id (required)."),
            "title" to prop("string", "New title."),
            "duration_minutes" to prop("integer", "New duration in minutes."),
            "fixed_time" to prop("string", "Local datetime 'yyyy-MM-dd HH:mm', or 'clear' to remove."),
            "recurrence" to prop("string", "none, daily, weekly, monthly, yearly."),
            "weekdays" to prop("string", "For weekly: comma list."),
            "recurrence_end" to prop("string", "Recurrence end date 'yyyy-MM-dd', or 'clear' to make it indefinite."),
            "info" to prop("string", "New info text.")), listOf("task_id"))

        d("delete_task", "Delete a task and all its sub-tasks.", mapOf(
            "task_id" to prop("integer", "Task id (required).")), listOf("task_id"))

        d("complete_task", "Mark a task complete or incomplete.", mapOf(
            "task_id" to prop("integer", "Task id (required)."),
            "completed" to prop("boolean", "True to mark done, false to reopen.")), listOf("task_id"))

        d("reorder_task", "Move a task between two others (or top/bottom) at its current level.", mapOf(
            "task_id" to prop("integer", "Task to move (required)."),
            "above_task_id" to prop("integer", "Task directly above new position; omit for very top."),
            "below_task_id" to prop("integer", "Task directly below new position; omit for very bottom.")), listOf("task_id"))

        d("move_task", "Reparent a task under another task (or to top level if new_parent_id omitted).", mapOf(
            "task_id" to prop("integer", "Task to move (required)."),
            "new_parent_id" to prop("integer", "New parent task id; omit for top level.")), listOf("task_id"))

        return JsonArray(listOf(buildJsonObject { putJsonArray("functionDeclarations") { decls.forEach { add(it) } } }))
    }

    private fun parseTime(s: String?): Long? {
        if (s.isNullOrBlank() || s.equals("clear", true)) return null
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(s.trim())?.time
        }.getOrNull()
    }

    private fun parseRecurrence(s: String?): Recurrence = when (s?.trim()?.lowercase()) {
        "daily" -> Recurrence.DAILY
        "weekly" -> Recurrence.WEEKLY
        "monthly" -> Recurrence.MONTHLY
        "yearly" -> Recurrence.YEARLY
        else -> Recurrence.NONE
    }

    private fun parseWeekdays(s: String?): Int {
        if (s.isNullOrBlank()) return 0
        var mask = 0
        s.lowercase().split(",", " ", ";").forEach { d ->
            when (d.trim().take(3)) {
                "mon" -> mask = mask or 1
                "tue" -> mask = mask or 2
                "wed" -> mask = mask or 4
                "thu" -> mask = mask or 8
                "fri" -> mask = mask or 16
                "sat" -> mask = mask or 32
                "sun" -> mask = mask or 64
            }
        }
        return mask
    }

    /** Parse 'yyyy-MM-dd' into end-of-day millis; 'clear'/blank -> null. */
    private fun parseEndDate(s: String?): Long? {
        if (s.isNullOrBlank() || s.equals("clear", true)) return null
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(s.trim())?.let { d ->
                val c = Calendar.getInstance().apply { time = d }
                c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59)
                c.set(Calendar.SECOND, 59); c.set(Calendar.MILLISECOND, 999)
                c.timeInMillis
            }
        }.getOrNull()
    }

    private fun JsonObject.str(k: String): String? = this[k]?.let { if (it is JsonPrimitive) it.content else null }
    private fun JsonObject.longOrNull(k: String): Long? = this[k]?.let {
        runCatching { (it as JsonPrimitive).content.toLong() }.getOrNull()
    }

    /** Execute one function call and return a natural-language summary. */
    suspend fun execute(name: String, args: JsonObject): String {
        return when (name) {
            "get_day_status" -> {
                val now = System.currentTimeMillis()
                val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss (EEEE)", Locale.getDefault())
                val tz = TimeZone.getDefault().id
                val remaining = repo.getRemainingMinutesToday()
                val allocated = repo.getAllocatedMinutesToday()
                "Device time: ${fmt.format(Date(now))} ($tz). Total allocated today: ${allocated}m. Remaining unallocated today: ${remaining}m."
            }

            "list_tasks" -> {
                val parent = args.longOrNull("parent_id")
                var list = repo.siblingsOf(parent)
                args.str("query")?.let { q -> list = list.filter { it.title.contains(q, true) } }
                if (list.isEmpty()) "No tasks found." else list.joinToString("\n") { t ->
                    "#${t.id} ${if (t.completed) "[done] " else ""}${t.title} (${t.durationMinutes}m)" +
                        (t.fixedTime?.let { " @ " + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(it)) } ?: "") +
                        (if (t.recurrence != Recurrence.NONE) " (${t.recurrence})" else "")
                }
            }

            "create_task" -> {
                val title = args.str("title") ?: return "Missing title."
                val duration = (args.longOrNull("duration_minutes") ?: 30L).coerceAtLeast(1L)
                val fixedTime = parseTime(args.str("fixed_time"))
                val recurrence = parseRecurrence(args.str("recurrence"))
                val weekdaysMask = parseWeekdays(args.str("weekdays"))
                val recurrenceEnd = if (recurrence != Recurrence.NONE) parseEndDate(args.str("recurrence_end")) else null
                val info = args.str("info") ?: ""

                // Validate if task fits today's remaining time (incl. overnight segment on today)
                val now = System.currentTimeMillis()
                val isToday = fixedTime == null || com.forgebuild.taskflow.data.DayAccounting.touchesDay(
                    com.forgebuild.taskflow.data.Task(title = "", rank = 0.0, durationMinutes = duration, fixedTime = fixedTime), now, now)
                if (isToday) {
                    val remaining = repo.getRemainingMinutesToday()
                    if (duration > remaining) {
                        return "Cannot create task \"$title\" (${duration}m): exceeds remaining unallocated time today (${remaining}m left). Schedule for another day or reduce duration."
                    }
                }

                val t = repo.create(
                    title = title,
                    parentId = args.longOrNull("parent_id"),
                    durationMinutes = duration,
                    fixedTime = fixedTime,
                    recurrence = recurrence,
                    weekdaysMask = weekdaysMask,
                    info = info,
                    recurrenceEndDate = recurrenceEnd
                )
                "Created task #${t.id}: \"${t.title}\" (${t.durationMinutes} min)."
            }

            "update_task" -> {
                val id = args.longOrNull("task_id") ?: return "Missing task_id."
                val t = repo.get(id) ?: return "Task #$id not found."
                val newDuration = args.longOrNull("duration_minutes") ?: t.durationMinutes
                val newFixed = if (args.str("fixed_time") != null) parseTime(args.str("fixed_time")) else t.fixedTime

                // Check remaining time if updating duration for today (incl. overnight segment)
                val now = System.currentTimeMillis()
                val isToday = newFixed == null || com.forgebuild.taskflow.data.DayAccounting.touchesDay(
                    t.copy(durationMinutes = newDuration.coerceAtLeast(1L), fixedTime = newFixed), now, now)
                if (isToday) {
                    val remainingWithOld = repo.getRemainingMinutesToday(excludeTaskId = t.id)
                    if (newDuration > remainingWithOld) {
                        return "Cannot update task #$id to ${newDuration}m: exceeds remaining time today (${remainingWithOld}m available)."
                    }
                }

                val newRecurrence = if (args.str("recurrence") != null) parseRecurrence(args.str("recurrence")) else t.recurrence
                val newRecurrenceEnd = when {
                    newRecurrence == Recurrence.NONE -> null
                    args.str("recurrence_end") != null -> parseEndDate(args.str("recurrence_end"))
                    else -> t.recurrenceEndDate
                }
                val updated = t.copy(
                    title = args.str("title") ?: t.title,
                    durationMinutes = newDuration.coerceAtLeast(1L),
                    fixedTime = newFixed,
                    recurrence = newRecurrence,
                    weekdaysMask = if (args.str("weekdays") != null) parseWeekdays(args.str("weekdays")) else t.weekdaysMask,
                    recurrenceEndDate = newRecurrenceEnd,
                    info = args.str("info") ?: t.info
                )
                repo.update(updated)
                "Updated task #$id: \"${updated.title}\"."
            }

            "delete_task" -> {
                val id = args.longOrNull("task_id") ?: return "Missing task_id."
                val t = repo.get(id) ?: return "Task #$id not found."
                repo.deleteTree(id)
                "Deleted \"${t.title}\" and its sub-tasks."
            }

            "complete_task" -> {
                val id = args.longOrNull("task_id") ?: return "Missing task_id."
                val t = repo.get(id) ?: return "Task #$id not found."
                val done = args["completed"]?.let { (it as JsonPrimitive).content.toBooleanStrictOrNull() } ?: true
                repo.setCompleted(id, done)
                "Marked \"${t.title}\" ${if (done) "complete" else "incomplete"}."
            }

            "reorder_task" -> {
                val id = args.longOrNull("task_id") ?: return "Missing task_id."
                repo.insertBetween(id, args.longOrNull("above_task_id"), args.longOrNull("below_task_id"))
                "Reordered task #$id."
            }

            "move_task" -> {
                val id = args.longOrNull("task_id") ?: return "Missing task_id."
                repo.move(id, args.longOrNull("new_parent_id"))
                "Moved task #$id."
            }

            else -> "Unknown action: $name"
        }
    }
}
