package com.forgebuild.clipforgeandroid.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Zernio publishing settings — full parity port of the motionssalt/clipforge
 * site (site/js/features/settings.js) and the pipeline validator
 * (pipeline/publish/zernio.py). The single source of truth document is
 * branding/zernio_settings.json in the user's clone:
 *
 *  { version, enabled, auto_publish,
 *    automatic_mode: "publish_now" | "smart_schedule",
 *    target_accounts: { platform: [accountIds] },
 *    smart_schedule: { timezone (IANA), interval_hours (1..8760),
 *                      preferred_time (HH:MM), queue_depth,
 *                      start_mode: "next_available" | "custom",
 *                      custom_start ("YYYY-MM-DDTHH:MM") } }
 *
 * Validation here mirrors zernio.py EXACTLY (same bounds + messages) so the app
 * and the site accept/reject identical input and never overwrite each other
 * (writes go through the GitHub contents API with SHA-safe updates).
 */
object ZernioSettings {

    const val SETTINGS_PATH = "branding/zernio_settings.json"
    const val ACCOUNTS_PATH = "branding/zernio_accounts.json"

    val PLATFORMS = listOf("tiktok", "youtube", "instagram")

    /** publish.yml workflow file + the modes the site uses. */
    const val PUBLISH_WORKFLOW = "publish.yml"

    private val TIME_RE = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
    private val IANA_RE = Regex("^(?:UTC|[A-Za-z_]+(?:/[A-Za-z_+\\-]+)+)$")
    private val CUSTOM_START_RE = Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}$")
    val POST_ID_RE = Regex("^[A-Za-z0-9._:\\-]{1,200}$")

    data class SmartSchedule(
        val timezone: String = "UTC",
        val intervalHours: Int = 6,
        val preferredTime: String = "05:00",
        val queueDepth: Int = 100,
        val startMode: String = "next_available",   // next_available | custom
        val customStart: String = ""                 // YYYY-MM-DDTHH:MM
    )

    data class Settings(
        val version: Int = 1,
        val enabled: Boolean = false,
        val autoPublish: Boolean = false,
        val automaticMode: String = "smart_schedule", // publish_now | smart_schedule
        val targetAccounts: Map<String, List<String>> = emptyMap(),
        val smart: SmartSchedule = SmartSchedule()
    )

    /** Parse the stored document, tolerating legacy interval_days (zernio.py). */
    fun parse(text: String?): Settings {
        if (text.isNullOrBlank()) return Settings()
        val j = try { JSONObject(text) } catch (_: Exception) { return Settings() }
        val smartJ = j.optJSONObject("smart_schedule") ?: JSONObject()
        // Legacy migration: interval_days -> interval_hours (x24), like zernio.py.
        var interval = smartJ.optInt("interval_hours", -1)
        if (interval < 0) interval = smartJ.optInt("interval_days", 0) * 24
        if (interval <= 0) interval = 6
        val targets = mutableMapOf<String, List<String>>()
        val tj = j.optJSONObject("target_accounts")
        if (tj != null) {
            for (p in PLATFORMS) {
                val arr = tj.optJSONArray(p) ?: continue
                val ids = mutableListOf<String>()
                for (i in 0 until arr.length()) ids.add(arr.optString(i))
                if (ids.isNotEmpty()) targets[p] = ids
            }
        }
        return Settings(
            version = j.optInt("version", 1),
            enabled = j.optBoolean("enabled", false),
            autoPublish = j.optBoolean("auto_publish", false),
            automaticMode = j.optString("automatic_mode", "smart_schedule"),
            targetAccounts = targets,
            smart = SmartSchedule(
                timezone = smartJ.optString("timezone", "UTC").ifBlank { "UTC" },
                intervalHours = interval,
                preferredTime = smartJ.optString("preferred_time", "05:00"),
                queueDepth = smartJ.optInt("queue_depth", 100),
                startMode = smartJ.optString("start_mode", "next_available"),
                customStart = smartJ.optString("custom_start", "")
            )
        )
    }

    fun toJson(s: Settings): String {
        val smart = JSONObject()
            .put("timezone", s.smart.timezone)
            .put("interval_hours", s.smart.intervalHours)
            .put("preferred_time", s.smart.preferredTime)
            .put("queue_depth", s.smart.queueDepth)
            .put("start_mode", s.smart.startMode)
            .put("custom_start", s.smart.customStart)
        val targets = JSONObject()
        for ((p, ids) in s.targetAccounts) {
            val arr = JSONArray()
            ids.forEach { arr.put(it) }
            targets.put(p, arr)
        }
        return JSONObject()
            .put("version", 1)
            .put("enabled", s.enabled)
            .put("auto_publish", s.autoPublish)
            .put("automatic_mode", s.automaticMode)
            .put("target_accounts", targets)
            .put("smart_schedule", smart)
            .toString(2)
    }

    /**
     * Validate exactly like zernio.py. Returns null when valid, else the first
     * error message (same wording as the pipeline validator).
     */
    fun validate(s: Settings): String? {
        val sm = s.smart
        if (s.automaticMode != "publish_now" && s.automaticMode != "smart_schedule")
            return "Automatic mode must be publish_now or smart_schedule."
        if (sm.intervalHours < 1 || sm.intervalHours > 8760)
            return "Posting interval must be between 1 and 8760 hours."
        if (!TIME_RE.matches(sm.preferredTime))
            return "Preferred posting time must use HH:MM (24-hour) format."
        if (!isValidIanaZone(sm.timezone))
            return "Unknown IANA timezone: ${sm.timezone}"
        if (sm.startMode != "next_available" && sm.startMode != "custom")
            return "Start mode must be next_available or custom."
        if (sm.startMode == "custom" && !CUSTOM_START_RE.matches(sm.customStart))
            return "A custom first slot is required when start mode is custom (YYYY-MM-DDTHH:MM)."
        if (sm.queueDepth < 1)
            return "Queue depth must be a positive whole number."
        return null
    }

    /** One-line live summary, e.g. "every 6h at 05:00 UTC, queue depth 100, next available". */
    fun summary(s: Settings): String {
        if (!s.enabled) return "Zernio publishing is off."
        if (!s.autoPublish) return "Automatic publishing off — publish per task."
        val sm = s.smart
        return if (s.automaticMode == "publish_now") {
            "Publish immediately on completion."
        } else {
            val start = if (sm.startMode == "custom" && sm.customStart.isNotBlank())
                "first slot ${sm.customStart}" else "next available"
            "every ${sm.intervalHours}h at ${sm.preferredTime} ${sm.timezone}, " +
                "queue depth ${sm.queueDepth}, $start"
        }
    }

    /** publish.yml targets_json shape: [{ platform, account_ids: [...] }]. */
    fun targetsJson(s: Settings, accounts: List<ClipForgeViewModelZernioAccountLike>): String {
        val active = accounts.filter { it.available }
        val out = JSONArray()
        for (p in PLATFORMS) {
            val wanted = s.targetAccounts[p] ?: continue
            val ids = wanted.filter { id -> active.any { it.platform == p && it.id == id } }
            if (ids.isEmpty()) continue
            val o = JSONObject().put("platform", p)
            val arr = JSONArray(); ids.forEach { arr.put(it) }
            o.put("account_ids", arr)
            out.put(o)
        }
        return out.toString()
    }

    /** Structural interface so this module doesn't depend on the ViewModel class. */
    interface ClipForgeViewModelZernioAccountLike {
        val id: String
        val platform: String
        val available: Boolean
    }

    /**
     * Real IANA validation + picker source: java.time.ZoneId ships the SAME IANA tz
     * database family the pipeline's Python zoneinfo validates against (both ship the
     * system tz database), so the picker and publish-time validation can never
     * disagree. Requires coreLibraryDesugaring (declared in app/build.gradle.kts) for
     * minSdk 26. NEVER a regex-only or hand-maintained-list check: a regex accepted
     * the bogus "Europe/Lagos" and broke every smart-schedule publish.
     */
    fun isValidIanaZone(value: String): Boolean {
        val v = value.trim()
        if (v.isEmpty()) return false
        return try { java.time.ZoneId.of(v); true } catch (_: Exception) { false }
    }

    /** Full genuine IANA zone list for the searchable picker (sorted, UTC pinned first). */
    fun ianaZones(): List<String> =
        listOf("UTC") + java.time.ZoneId.getAvailableZoneIds().filter { it != "UTC" }.sorted()

    /** Back-compat curated suggestions row (chips); the authoritative list is ianaZones(). */
    val COMMON_TIMEZONES = listOf(
        "UTC", "Europe/London", "Europe/Paris", "Europe/Berlin", "Europe/Madrid",
        "Europe/Rome", "Europe/Amsterdam",
        "Africa/Lagos", "Africa/Johannesburg", "Africa/Nairobi", "Africa/Cairo",
        "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles",
        "America/Sao_Paulo", "America/Toronto", "America/Mexico_City",
        "Asia/Dubai", "Asia/Kolkata", "Asia/Singapore", "Asia/Tokyo", "Asia/Hong_Kong",
        "Asia/Jakarta", "Asia/Manila", "Australia/Sydney", "Australia/Melbourne",
        "Pacific/Auckland", "Pacific/Honolulu"
    )
}
