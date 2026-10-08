package com.forgebuild.clipforgeandroid.data

/**
 * Source classification — verbatim port of `classifySourceText` from
 * site/js/wizard.js (motionssalt/clipforge).
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

    // Regexes ported 1:1 from site/js/wizard.js (all case-insensitive).
    private val MAGNET_RE = Regex("^magnet:\\?", RegexOption.IGNORE_CASE)
    private val TELEGRAM_PUBLIC_POST_RE = Regex(
        "^https?://(?:t\\.me|telegram\\.me)/(?:s/)?[A-Za-z0-9_]{5,64}/[1-9][0-9]*(?:[/?#]|$)",
        RegexOption.IGNORE_CASE
    )
    private val DRIVE_RE = Regex("^https?://(?:drive|docs)\\.google\\.com/", RegexOption.IGNORE_CASE)
    private val URL_RE = Regex("^https?://", RegexOption.IGNORE_CASE)

    val YOUTUBE_HOSTS = listOf("youtube.com", "youtu.be", "youtube-nocookie.com")
    private val YT_VIDEO_ID_RE = Regex("^[A-Za-z0-9_-]{11}$")

    /** §5 "Deliberately disabled" hosts — rejected at intake. YouTube unblocked. */
    val DISABLED_SOCIAL_HOSTS = listOf(
        "vm.tiktok.com", "vt.tiktok.com", "tiktok.com",
        "fb.watch", "facebook.com", "instagram.com",
        "twitter.com", "x.com", "vimeo.com", "redd.it", "reddit.com"
    )

    /** Classification outcome: a recognized {kind, value}, or the error text. */
    sealed class Result {
        data class Kind(val kind: String, val value: String) : Result()
        data class Invalid(val error: String) : Result()
    }

    /**
     * Extract an 11-char YouTube video ID from various YouTube URL formats.
     * Supports watch?v=, youtu.be/, /shorts/, /embed/, /v/.
     */
    fun extractYoutubeVideoId(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val host = hostOf(trimmed)
            val isYtHost = YOUTUBE_HOSTS.any { host == it || host.endsWith(".$it") }
            if (!isYtHost) return null

            // youtu.be/<id>
            if (host == "youtu.be" || host.endsWith(".youtu.be")) {
                val path = try {
                    java.net.URI(trimmed).path?.trimStart('/') ?: ""
                } catch (_: Exception) {
                    trimmed.substringAfter("youtu.be/").substringBefore('?').substringBefore('&')
                }
                val parts = path.split('/')
                if (parts.isNotEmpty() && parts[0].isNotEmpty()) {
                    val candidate = parts[0].substringBefore('?').substringBefore('&')
                    if (YT_VIDEO_ID_RE.matches(candidate)) return candidate
                }
            }

            // query: ?v=<id>
            try {
                val uri = java.net.URI(trimmed)
                val query = uri.query
                if (!query.isNullOrEmpty()) {
                    val params = query.split('&')
                    for (p in params) {
                        val kv = p.split('=', limit = 2)
                        if (kv.size == 2 && kv[0] == "v") {
                            val candidate = kv[1]
                            if (YT_VIDEO_ID_RE.matches(candidate)) return candidate
                        }
                    }
                }

                // /shorts/<id> or /embed/<id> or /v/<id>
                val pathParts = (uri.path ?: "").split('/').filter { it.isNotEmpty() }
                if (pathParts.size >= 2 && pathParts[0] in listOf("shorts", "embed", "v")) {
                    val candidate = pathParts[1].substringBefore('?').substringBefore('&')
                    if (YT_VIDEO_ID_RE.matches(candidate)) return candidate
                }
            } catch (_: Exception) {
                val vParam = trimmed.substringAfter("v=", "").substringBefore('&').substringBefore('#')
                if (YT_VIDEO_ID_RE.matches(vParam)) return vParam
            }

            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Normalize YouTube URL to canonical https://www.youtube.com/watch?v=<id>
     * Strips playlist/radio/timestamp params.
     */
    fun normalizeYoutubeUrl(url: String): String? {
        val id = extractYoutubeVideoId(url) ?: return null
        return "https://www.youtube.com/watch?v=$id"
    }

    /**
     * Classify a text source exactly like wizard.js step 1.
     * Returns Kind(kind, value) or Invalid(error message).
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
                    "Links from $blocked are not supported. Supported sources are public YouTube videos, direct video links, Google Drive links, magnets, .torrent files, or public Telegram channel posts."
                )
            }

            val ytCanonical = normalizeYoutubeUrl(value)
            if (ytCanonical != null) {
                return Result.Kind("youtube", ytCanonical)
            }

            if (DRIVE_RE.containsMatchIn(value)) return Result.Kind("drive", value)
            return Result.Kind("url", value)
        }

        return Result.Invalid(
            "That does not look like a supported source. Send a public YouTube link, a direct video URL (https://…), a Google Drive link, a magnet URI, a .torrent file, a public t.me channel-post link, or the video itself."
        )
    }

    /** Port of wizard.js hostOf() — hostname, lowercase, '' when unparseable. */
    private fun hostOf(url: String): String = try {
        java.net.URI(url).host?.lowercase()?.removeSuffix(".") ?: ""
    } catch (_: Exception) {
        val clean = url.substringAfter("://").substringBefore('/').substringBefore('?')
        clean.lowercase().removeSuffix(".")
    }
}
