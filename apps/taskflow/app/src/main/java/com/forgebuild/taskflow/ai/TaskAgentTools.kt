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
import java.util.Date
import java.util.Locale

/** Declares Gemini function-calling schemas and executes them against the local Room DB. */
class TaskAgentTools(private val repo: TaskRepository) {

    private fun prop(type: String, desc: String): JsonObject = buildJsonObject {
        put("type", type); put("description", desc)
    }

    /** The real tools array for the API (single tool object with all declarations). */
    fun apiTools(): JsonArray {
        val decls = mutableListOf<JsonObject>()
        fun d(name: String, desc: String, props: Map<String, JsonObject>, required: List<String> = emptyList()) {
            decls.add(buildJsonObject {
                put("name", name); put("description", desc)
                putJsonObject("parameters") {
                    put("type", "object")
                    putJsonObject("properties") { props.forEach { (k, v) -> put(k, v) } }
                    if (required.isNotEmpty()) putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
                }
            })
        }
        d("list_tasks", "List tasks. Use parent_id for a sub-task level, omit for top level. Optionally filter by query.", mapOf(
            "parent_id" to prop("integer", "Parent task id; omit for top level."),
            "query" to prop("string", "Optional case-insensitive title filter.")))
        d("create_task", "Create a task. Infer sensible defaults for anything the user leaves vague.", mapOf(
            "title" to prop("string", "Task title (required)."),
            "parent_id" to prop("integer", "Parent id for a sub-task."),
            "fixed_time" to prop("string", "Local datetime 'yyyy-MM-dd HH:mm' if a due time applies."),
            "recurrence" to prop("string", "one of none,daily,weekly,monthly,yearly."),
            "weekdays" to prop("string", "For weekly: comma list like mon,wed,fri."),
            "info" to prop("string", "Free-text notes on how to do the task.")), listOf("title"))
        d("update_task", "Update any task field.", mapOf(
            "task_id" to prop("integer", "Task id (required)."),
            "title" to prop("string", "New title."),
            "fixed_time" to prop("string", "Local datetime, or 'clear'."),
            "recurrence" to prop("string", "none,daily,weekly,monthly,yearly."),
            "weekdays" to prop("string", "For weekly: comma list."),
            "info" to prop("string", "New info text.")), listOf("task_id"))
        d("delete_task", "Delete a task and all its sub-tasks.", mapOf(
            "task_id" to prop("integer", "Task id (required).")), listOf("task_id"))
        d("complete_task", "Mark a task complete or incomplete.", mapOf(
            "task_id" to prop("integer", "Task id (required)."),
            "completed" to prop("boolean", "Default true.")), listOf("task_id"))
        d("reorder_task", "Move a task between two others (or to the very top/bottom) in its list.", mapOf(
            "task_id" to prop("integer", "Task to move (required)."),
            "above_task_id" to prop("integer", "Task directly above the new position; omit for very top."),
            "below_task_id" to prop("integer", "Task directly below the new position; omit for very bottom.")), listOf("task_id"))
        d("move_task", "Reparent a task under another (or to top level if new_parent_id omitted).", mapOf(
            "task_id" to prop("integer", "Task to move (required)."),
            "new_parent_id" to prop("integer", "New parent; omit for top level.")), listOf("task_id"))
        return JsonArray(listOf(buildJsonObject { putJsonArray("function_declarations") { decls.forEach { add(it) } } }))
    }

    private fun parseTime(s: String?): Long? {
        if (s.isNullOrBlank() || s.equals("clear", true)) return null
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(s.trim())?.time
        }.getOrNull()
    }

    private fun parseRecurrence(s: String?): Recurrence = when (s?.trim()?.lowercase()) {
        "daily" -> Recurrence.DAILY; "weekly" -> Recurrence.WEEKLY
        "monthly" -> Recurrence.MONTHLY; "yearly" -> Recurrence.YEARLY; else -> Recurrence.NONE
    }

    private fun parseWeekdays(s: String?): Int {
        if (s.isNullOrBlank()) return 0
        var mask = 0
        s.lowercase().split(",", " ", ";").forEach { d ->
            when (d.trim().take(3)) {
                "mon" -> mask = mask or 1; "tue" -> mask = mask or 2; "wed" -> mask = mask or 4
                "thu" -> mask = mask or 8; "fri" -> mask = mask or 16; "sat" -> mask = mask or 32
                "sun" -> mask = mask or 64
            }
        }
        return mask
    }

    private fun JsonObject.str(k: String): String? = this[k]?.let { if (it is JsonPrimitive) it.content else null }
    private fun JsonObject.longOrNull(k: String): Long? = this[k]?.let {
        runCatching { (it as JsonPrimitive).content.toLong() }.getOrNull() }

    /** Execute one function call and return a natural-language summary. */
    suspend fun execute(name: String, args: JsonObject): String {
        return when (name) {
            "list_tasks" -> {
                val parent = args.longOrNull("parent_id")
                var list = repo.siblingsOf(parent)
                args.str("query")?.let { q -> list = list.filter { it.title.contains(q, true) } }
                if (list.isEmpty()) "No tasks found." else list.joinToString("\n") { t ->
                    "#${t.id} ${if (t.completed) "[done] " else ""}${t.title}" +
                        (t.fixedTime?.let { " @ " + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(it)) } ?: "") +
                        (if (t.recurrence != Recurrence.NONE) " (${t.recurrence})" else "")
                }
            }
            "create_task" -> {
                val t = repo.create(
                    title = args.str("title") ?: return "Missing title.",
                    parentId = args.longOrNull("parent_id"),
                    fixedTime = parseTime(args.str("fixed_time")),
                    recurrence = parseRecurrence(args.str("recurrence")),
                    weekdaysMask = parseWeekdays(args.str("weekdays")),
                    info = args.str("info") ?: "")
                "Created task #${t.id}: \"${t.title}\"."
            }
            "update_task" -> {
                val id = args.longOrNull("task_id") ?: return "Missing task_id."
                val t = repo.get(id) ?: return "Task #$id not found."
                val updated = t.copy(
                    title = args.str("title") ?: t.title,
                    fixedTime = if (args.str("fixed_time") != null) parseTime(args.str("fixed_time")) else t.fixedTime,
                    recurrence = if (args.str("recurrence") != null) parseRecurrence(args.str("recurrence")) else t.recurrence,
                    weekdaysMask = if (args.str("weekdays") != null) parseWeekdays(args.str("weekdays")) else t.weekdaysMask,
                    info = args.str("info") ?: t.info)
                repo.update(updated)
                "Updated task #$id."
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
