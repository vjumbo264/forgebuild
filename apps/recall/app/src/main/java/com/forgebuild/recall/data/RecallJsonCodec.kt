package com.forgebuild.recall.data

/**
 * Pure-Kotlin JSON codec for [RecallAppState] so serialization works identically
 * on Android runtime and JVM unit tests (without depending on `org.json` stubs
 * or adding external serialization dependencies).
 */
object RecallJsonCodec {

    fun encode(state: RecallAppState): String = buildString {
        append('{')
        append("\"status\":"); appendString(state.status.name); append(',')
        append("\"headsUpNotificationsEnabled\":"); append(state.headsUpNotificationsEnabled); append(',')
        append("\"highPrecisionAlarmClockMode\":"); append(state.highPrecisionAlarmClockMode); append(',')
        append("\"lastFiredBoundaryMs\":"); append(state.lastFiredBoundaryMs); append(',')
        append("\"onboardingDone\":"); append(state.onboardingDone); append(',')
        append("\"intervals\":[")
        state.intervals.forEachIndexed { idx, interval ->
            if (idx > 0) append(',')
            append('{')
            append("\"id\":"); appendString(interval.id); append(',')
            append("\"label\":"); appendString(interval.label); append(',')
            append("\"minutes\":"); append(interval.minutes); append(',')
            append("\"enabled\":"); append(interval.enabled); append(',')
            append("\"sound\":"); appendString(interval.sound.name); append(',')
            append("\"volume\":"); append(interval.volume.coerceIn(0f, 1f)); append(',')
            append("\"priority\":"); append(interval.priority)
            append('}')
        }
        append("]}")
    }

    fun decode(raw: String?): RecallAppState? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val root = Parser(raw).parseValue() as? Map<*, *> ?: return null
            val status = RecallStatus.fromName(root["status"] as? String)
            val headsUp = (root["headsUpNotificationsEnabled"] as? Boolean) ?: true
            val highPrecision = (root["highPrecisionAlarmClockMode"] as? Boolean) ?: false
            val lastFired = (root["lastFiredBoundaryMs"] as? Number)?.toLong() ?: 0L
            val onboarding = (root["onboardingDone"] as? Boolean) ?: false
            val rawIntervals = root["intervals"] as? List<*>
            val intervals = if (rawIntervals != null) {
                rawIntervals.mapNotNull { item ->
                    val m = item as? Map<*, *> ?: return@mapNotNull null
                    val id = (m["id"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val minutes = (m["minutes"] as? Number)?.toInt()?.coerceIn(1, 1440) ?: return@mapNotNull null
                    val label = (m["label"] as? String)?.takeIf { it.isNotBlank() }
                        ?: RecallDefaults.defaultLabelForMinutes(minutes)
                    val enabled = (m["enabled"] as? Boolean) ?: true
                    val sound = RecallSound.fromName(m["sound"] as? String)
                    val volume = ((m["volume"] as? Number)?.toFloat() ?: 0.8f).coerceIn(0f, 1f)
                    val priority = (m["priority"] as? Number)?.toInt()
                        ?: RecallDefaults.defaultPriorityForMinutes(minutes)
                    RecallInterval(
                        id = id,
                        label = label,
                        minutes = minutes,
                        enabled = enabled,
                        sound = sound,
                        volume = volume,
                        priority = priority,
                    )
                }
            } else {
                RecallDefaults.defaultIntervals()
            }
            RecallAppState(
                status = status,
                intervals = intervals,
                headsUpNotificationsEnabled = headsUp,
                highPrecisionAlarmClockMode = highPrecision,
                lastFiredBoundaryMs = lastFired,
                onboardingDone = onboarding,
            )
        }.getOrNull()
    }

    private fun StringBuilder.appendString(s: String) {
        append('"')
        for (ch in s) {
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        append(ch)
                    }
                }
            }
        }
        append('"')
    }

    private class Parser(private val src: String) {
        private var pos = 0

        fun parseValue(): Any? {
            skipWs()
            if (pos >= src.length) error("Unexpected EOF")
            return when (src[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't', 'f' -> parseBoolean()
                'n' -> parseNull()
                '-', in '0'..'9' -> parseNumber()
                else -> error("Unexpected char '${src[pos]}' at $pos")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            pos++ // '{'
            val map = LinkedHashMap<String, Any?>()
            skipWs()
            if (pos < src.length && src[pos] == '}') {
                pos++
                return map
            }
            while (true) {
                skipWs()
                val key = parseString()
                skipWs()
                requireChar(':')
                val value = parseValue()
                map[key] = value
                skipWs()
                if (pos < src.length && src[pos] == ',') {
                    pos++
                } else {
                    requireChar('}')
                    return map
                }
            }
        }

        private fun parseArray(): List<Any?> {
            pos++ // '['
            val list = ArrayList<Any?>()
            skipWs()
            if (pos < src.length && src[pos] == ']') {
                pos++
                return list
            }
            while (true) {
                list.add(parseValue())
                skipWs()
                if (pos < src.length && src[pos] == ',') {
                    pos++
                } else {
                    requireChar(']')
                    return list
                }
            }
        }

        private fun parseString(): String {
            requireChar('"')
            val sb = StringBuilder()
            while (pos < src.length) {
                val ch = src[pos++]
                if (ch == '"') return sb.toString()
                if (ch == '\\') {
                    if (pos >= src.length) error("Unterminated escape")
                    when (val esc = src[pos++]) {
                        '"', '\\', '/' -> sb.append(esc)
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            val hex = src.substring(pos, pos + 4)
                            pos += 4
                            sb.append(hex.toInt(16).toChar())
                        }
                        else -> error("Unknown escape '\\$esc'")
                    }
                } else {
                    sb.append(ch)
                }
            }
            error("Unterminated string")
        }

        private fun parseBoolean(): Boolean {
            return if (src.startsWith("true", pos)) {
                pos += 4; true
            } else if (src.startsWith("false", pos)) {
                pos += 5; false
            } else error("Invalid boolean at $pos")
        }

        private fun parseNull(): Any? {
            if (src.startsWith("null", pos)) {
                pos += 4; return null
            }
            error("Invalid null at $pos")
        }

        private fun parseNumber(): Number {
            val start = pos
            while (pos < src.length && src[pos] in "0123456789.-+eE") pos++
            val token = src.substring(start, pos)
            return if ('.' in token || 'e' in token || 'E' in token) {
                token.toDouble()
            } else {
                token.toLong()
            }
        }

        private fun skipWs() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }

        private fun requireChar(c: Char) {
            if (pos >= src.length || src[pos] != c) error("Expected '$c' at $pos")
            pos++
        }
    }
}
