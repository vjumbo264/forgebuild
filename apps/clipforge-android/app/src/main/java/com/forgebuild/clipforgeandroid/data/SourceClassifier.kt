package com.forgebuild.clipforgeandroid.data

/**
 * Source classification — verbatim port of `classifySourceText` from
 * bot/src/wizard.js (motionssalt/clipforge).
 *
 * ORDERING IS THE CONTRACT (operator fix #1): the MAGNET check MUST run
 * BEFORE any generic URL fallback. A magnet URI (`magnet:?xt=urn:btih:...`)
 * is technically also a URI, so a generic "is this a URL" check can wrongly
 * capture it — which is exactly how a magnet-source task ended up written
 * with `"source": {"kind": "url", ...}` in stage-a-request.json and failed
 * immediately at Stage A ingest. The bot checks `MAGNET_RE = /^magnet:\?/i`
 * first and returns `{ kind: 'magnet', value }` before ever reaching the
 * generic URL case; this port preserves that order exactly.
 *
 * The classifier's verdict always wins over a manually picked source-type
 * chip in the New Task wizard (except for torrent_file, which is a file
 * upload, not a text classification).
 */
object SourceClassifier {

    // Regexes ported 1:1 from bot/src/wizard.js (all case-insensitive there).
    private val MAGNET_RE = Regex("^magnet:\\?", RegexOption.IGNORE_CASE)
    private val TELEGRAM_PUBLIC_POST_RE = Regex(
        "^https?://(?:t\\.me|telegram\\.me)/(?:s/)?[A-Za-z0-9_]{5,64}/[1-9][0-9]*(?:[/?#]|\$)",
        RegexOption.IGNORE_CASE
    )
    private val DRIVE_RE = Regex("^https?://(?:drive|docs)\\.google\\.com/", RegexOption.IGNORE_CASE)
    private val URL_RE = Regex("^https?://", RegexOption.IGNORE_CASE)

    /** §5 "Deliberately disabled" hosts — rejected at intake (bot wizard.js). */
    val DISABLED_SOCIAL_HOSTS = listOf(
        "youtube-nocookie.com", "youtu.be", "youtube.com",
        "vm.tiktok.com", "vt.tiktok.com", "tiktok.com",
        "fb.watch", "facebook.com", "instagram.com",
        "twitter.com", "x.com", "vimeo.com", "redd.it", "reddit.com"
    )

    /** Classification outcome: a recognized {kind, value}, or the bot's error text. */
    sealed class Result {
        data class Kind(val kind: String, val value: String) : Result()
        data class Invalid(val error: String) : Result()
    }

    /**
     * Classify a text source exactly like the bot's wizard step 1.
     * Returns Kind(kind, value) or Invalid(bot error message).
     */
    fun classify(text: String): Result {
        val value = text.trim()
        if (value.isEmpty()) {
            return Result.Invalid("Send a direct link, a magnet URI, a .torrent file, or a video.")
        }
        // MAGNET FIRST — before the generic URL fallthrough. Never reorder.
        if (MAGNET_RE.containsMatchIn(value)) return Result.Kind("magnet", value)
        if (TELEGRAM_PUBLIC_POST_RE.containsMatchIn(value)) return Result.Kind("telegram_channel", value)
        if (URL_RE.containsMatchIn(value)) {
            val host = hostOf(value)
            val blocked = DISABLED_SOCIAL_HOSTS.firstOrNull { host == it || host.endsWith(".$it") }
            if (blocked != null) {
                return Result.Invalid(
                    "Links from $blocked are not supported. Put the video on a public Telegram channel and send the t.me link, or send the video file directly."
                )
            }
            if (DRIVE_RE.containsMatchIn(value)) return Result.Kind("drive", value)
            return Result.Kind("url", value)
        }
        return Result.Invalid(
            "That does not look like a supported source. Send a direct video URL (https://…), a Google Drive link, a magnet URI, a .torrent file, a public t.me channel-post link, or the video itself."
        )
    }

    /** Port of wizard.js hostOf() — hostname, lowercase, '' when unparseable. */
    private fun hostOf(url: String): String = try {
        java.net.URI(url).host?.lowercase() ?: ""
    } catch (_: Exception) {
        ""
    }
}
