package com.forgebuild.otgenabler

import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val command: String
) {
    val isSuccess: Boolean get() = exitCode == 0
    val combinedError: String
        get() = buildString {
            if (stderr.isNotBlank()) append(stderr.trim())
            if (stdout.isNotBlank() && !isSuccess) {
                if (isNotEmpty()) append(" | ")
                append(stdout.trim())
            }
            if (isEmpty() && !isSuccess) {
                append("exit code $exitCode")
            }
        }
}

data class LogEntry(
    val id: Long = System.nanoTime(),
    val timestamp: String,
    val type: LogType,
    val message: String
)

enum class LogType {
    INFO, SUCCESS, ERROR, COMMAND, WARNING
}

enum class RootStatus {
    NOT_CHECKED,
    GRANTED,
    DENIED,
    NOT_ROOTED
}

object RootCommander {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _rootStatus = MutableStateFlow(RootStatus.NOT_CHECKED)
    val rootStatus: StateFlow<RootStatus> = _rootStatus.asStateFlow()

    private val _rootFlavor = MutableStateFlow("Unknown")
    val rootFlavor: StateFlow<String> = _rootFlavor.asStateFlow()

    private val _rootIdOutput = MutableStateFlow("")
    val rootIdOutput: StateFlow<String> = _rootIdOutput.asStateFlow()

    private val _lastWriteResults = MutableStateFlow<List<SafeWriteResult>>(emptyList())
    val lastWriteResults: StateFlow<List<SafeWriteResult>> = _lastWriteResults.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun log(type: LogType, message: String) {
        val entry = LogEntry(
            timestamp = timeFormat.format(Date()),
            type = type,
            message = message
        )
        _logs.value = (_logs.value + entry).takeLast(600)
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun clearWriteSession() {
        _lastWriteResults.value = emptyList()
    }

    @Synchronized
    private fun recordWriteResult(result: SafeWriteResult) {
        _lastWriteResults.value = (_lastWriteResults.value + result).takeLast(200)
    }

    private fun shellSingleQuote(raw: String): String {
        return "'" + raw.replace("'", "'"'"'") + "'"
    }

    suspend fun checkRoot(forceRefresh: Boolean = false): RootStatus = withContext(Dispatchers.IO) {
        if (!forceRefresh && _rootStatus.value != RootStatus.NOT_CHECKED) {
            return@withContext _rootStatus.value
        }

        log(LogType.INFO, "Checking root privileges...")

        val suPaths = arrayOf(
            "/data/adb/ksu/bin/su",
            "/data/adb/ap/bin/su",
            "/data/adb/magisk/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su"
        )

        val foundBinary = suPaths.firstOrNull { File(it).exists() }
        val flavor = when {
            File("/data/adb/ksu").exists() -> "KernelSU"
            File("/data/adb/ap").exists() -> "APatch"
            File("/data/adb/magisk").exists() -> "Magisk"
            foundBinary != null -> "su (${File(foundBinary).name})"
            else -> "Not Rooted / Standard"
        }
        _rootFlavor.value = flavor

        val result = execInternal("id")
        val status = if (result.isSuccess && result.stdout.contains("uid=0")) {
            _rootIdOutput.value = result.stdout.trim()
            if (result.stdout.contains("u:r:ksu:s0") && _rootFlavor.value == "Not Rooted / Standard") {
                _rootFlavor.value = "KernelSU"
            }
            log(LogType.SUCCESS, "Root granted (${_rootFlavor.value}): ${result.stdout.trim()}")
            RootStatus.GRANTED
        } else {
            val alt = execInteractive("id")
            if (alt.isSuccess && alt.stdout.contains("uid=0")) {
                _rootIdOutput.value = alt.stdout.trim()
                if (alt.stdout.contains("u:r:ksu:s0")) {
                    _rootFlavor.value = "KernelSU"
                }
                log(LogType.SUCCESS, "Root granted (${_rootFlavor.value}): ${alt.stdout.trim()}")
                RootStatus.GRANTED
            } else {
                val errDetail = result.combinedError.ifBlank { alt.combinedError.ifBlank { "no uid=0 returned" } }
                if (foundBinary != null) {
                    log(LogType.ERROR, "Root denied or pending grant: $errDetail")
                    RootStatus.DENIED
                } else {
                    log(LogType.WARNING, "No root shell available: $errDetail")
                    RootStatus.NOT_ROOTED
                }
            }
        }

        _rootStatus.value = status
        status
    }

    suspend fun getSELinuxStatus(): String = withContext(Dispatchers.IO) {
        val res = execSilent("getenforce 2>&1")
        if (res.isSuccess && res.stdout.isNotBlank()) res.stdout.trim() else "Unknown"
    }

    suspend fun setSELinuxMode(permissive: Boolean): CommandResult = withContext(Dispatchers.IO) {
        val targetArg = if (permissive) "0" else "1"
        val targetName = if (permissive) "Permissive" else "Enforcing"
        log(LogType.WARNING, "Setting SELinux mode to $targetName (setenforce $targetArg)...")
        val res = exec("setenforce $targetArg")
        delay(100L)
        val verify = getSELinuxStatus()
        if (res.isSuccess && verify.equals(targetName, ignoreCase = true)) {
            log(LogType.SUCCESS, "SELinux verified as $verify")
        } else {
            log(LogType.ERROR, "SELinux change unverified: exit=${res.exitCode}, current=$verify, err=${res.combinedError}")
        }
        res
    }

    /**
     * THE SINGLE SAFE WRITE HELPER for sysfs nodes (Section 6.1).
     * - Runs as root via su -c, capturing stdout, stderr, and exit code.
     * - Records existence, permissions, writability as root, and current value before writing.
     * - Uses `tee` so kernel write() errno (EINVAL, EOPNOTSUPP, EACCES, EBUSY) is always emitted to stderr.
     * - Waits 200ms, reads back, and parses active value (including `[host] device` selector format).
     * - Logs full structured result to Root Logs with real error text (never blank).
     */
    suspend fun safeWriteSysfs(
        path: String,
        requested: String,
        readbackDelayMs: Long = 200L
    ): SafeWriteResult = withContext(Dispatchers.IO) {
        val cleanPath = path.trim()
        val cleanReq = requested.trim()

        if (SysfsReadbackParser.shouldExcludePath(cleanPath)) {
            val excludedRes = SafeWriteResult(
                path = cleanPath,
                kind = TargetKind.SYSFS,
                requested = cleanReq,
                exists = false,
                permissions = "excluded",
                writable = false,
                before = "",
                after = "",
                exitCode = -1,
                ok = false,
                stderr = "Excluded virtual/non-controllable sysfs node"
            )
            recordWriteResult(excludedRes)
            log(LogType.WARNING, excludedRes.toStructuredLog())
            return@withContext excludedRes
        }

        val qPath = shellSingleQuote(cleanPath)
        val preCmd = """
            P=$qPath
            if [ ! -e "${'$'}P" ]; then
              printf 'EXISTS=0
'
            else
              PERMS=${'$'}(ls -ldL "${'$'}P" 2>/dev/null | awk '{print ${'$'}1}')
              if [ -z "${'$'}PERMS" ]; then PERMS=${'$'}(ls -ld "${'$'}P" 2>/dev/null | awk '{print ${'$'}1}'); fi
              if [ -w "${'$'}P" ]; then TW=1; else TW=0; fi
              printf 'EXISTS=1
PERMS=%s
TW=%s
BEFORE_BEGIN
' "${'$'}PERMS" "${'$'}TW"
              cat "${'$'}P" 2>&1 || printf '(unreadable)'
              printf '
BEFORE_END
'
            fi
        """.trimIndent()

        val preRes = execInternal(preCmd)
        val preOut = preRes.stdout
        if (!preOut.contains("EXISTS=1")) {
            val missingErr = preRes.combinedError.ifBlank { "ENOENT: Sysfs node does not exist" }
            val notFound = SafeWriteResult(
                path = cleanPath,
                kind = TargetKind.SYSFS,
                requested = cleanReq,
                exists = false,
                permissions = "missing",
                writable = false,
                before = "(missing)",
                after = "(missing)",
                exitCode = 2,
                ok = false,
                stderr = missingErr
            )
            recordWriteResult(notFound)
            log(LogType.WARNING, notFound.toStructuredLog())
            return@withContext notFound
        }

        val perms = preOut.lineSequence()
            .firstOrNull { it.startsWith("PERMS=") }
            ?.removePrefix("PERMS=")
            ?.trim()
            .orEmpty()
            .ifBlank { "unknown" }

        val tw = preOut.lineSequence()
            .firstOrNull { it.startsWith("TW=") }
            ?.removePrefix("TW=")
            ?.trim() == "1"

        val beforeRaw = preOut
            .substringAfter("BEFORE_BEGIN
", "")
            .substringBefore("
BEFORE_END", "")
            .trim()
            .ifBlank { "(empty)" }

        val isWritable = SysfsReadbackParser.isSysfsModeWritable(perms, rootTestWritable = tw)
        if (!isWritable) {
            val roResult = SafeWriteResult(
                path = cleanPath,
                kind = TargetKind.SYSFS,
                requested = cleanReq,
                exists = true,
                permissions = perms,
                writable = false,
                before = beforeRaw,
                after = beforeRaw,
                exitCode = 13,
                ok = false,
                stderr = "EACCES: Read-only sysfs node ($perms, rootWritable=$tw); kernel exposes no store handler"
            )
            recordWriteResult(roResult)
            log(LogType.WARNING, roResult.toStructuredLog())
            return@withContext roResult
        }

        val qVal = shellSingleQuote(cleanReq)
        val writeCmd = """
            P=$qPath
            V=$qVal
            if command -v tee >/dev/null 2>&1; then
              printf '%s
' "${'$'}V" | tee "${'$'}P" >/dev/null
            else
              printf '%s
' "${'$'}V" > "${'$'}P"
            fi
        """.trimIndent()

        log(LogType.COMMAND, "write sysfs: $cleanPath <= "$cleanReq" (before="$beforeRaw", perms=$perms)")
        val writeRes = execInternal(writeCmd)

        if (readbackDelayMs > 0) {
            delay(readbackDelayMs)
        }

        val postRes = execInternal("cat $qPath 2>&1")
        val afterRaw = if (postRes.isSuccess) {
            postRes.stdout.trim().ifBlank { "(empty)" }
        } else {
            "(unreadable: ${postRes.combinedError})"
        }

        val matched = writeRes.isSuccess &&
            postRes.isSuccess &&
            SysfsReadbackParser.matchesIntent(cleanReq, afterRaw, cleanPath)

        val errReason = when {
            matched -> ""
            !writeRes.isSuccess -> {
                val rawErr = writeRes.combinedError
                if (rawErr.isNotBlank() && !rawErr.startsWith("exit code")) {
                    "$rawErr (exit=${writeRes.exitCode})"
                } else {
                    "Kernel driver rejected write "$cleanReq" with exit=${writeRes.exitCode} (likely EOPNOTSUPP/EINVAL; readback="$afterRaw")"
                }
            }
            !postRes.isSuccess -> {
                "Write exited 0, but readback failed: ${postRes.combinedError}"
            }
            else -> {
                "Readback mismatch: write exited 0, but node reads "$afterRaw" instead of "$cleanReq" (driver ignored write)"
            }
        }

        val finalResult = SafeWriteResult(
            path = cleanPath,
            kind = TargetKind.SYSFS,
            requested = cleanReq,
            exists = true,
            permissions = perms,
            writable = true,
            before = beforeRaw,
            after = afterRaw,
            exitCode = writeRes.exitCode,
            ok = matched,
            stderr = errReason
        )
        recordWriteResult(finalResult)
        log(if (matched) LogType.SUCCESS else LogType.ERROR, finalResult.toStructuredLog())
        finalResult
    }

    /**
     * Safe write helper for Android system properties (`setprop` + `getprop` readback).
     */
    suspend fun safeWriteProp(
        propName: String,
        requested: String,
        readbackDelayMs: Long = 200L
    ): SafeWriteResult = withContext(Dispatchers.IO) {
        val cleanProp = propName.trim()
        val cleanReq = requested.trim()
        val qProp = shellSingleQuote(cleanProp)
        val qVal = shellSingleQuote(cleanReq)

        val beforeRes = execInternal("getprop $qProp 2>&1")
        val beforeVal = beforeRes.stdout.trim()

        log(LogType.COMMAND, "setprop $cleanProp "$cleanReq" (before="$beforeVal")")
        val writeRes = execInternal("setprop $qProp $qVal")
        if (readbackDelayMs > 0) delay(readbackDelayMs)

        val afterRes = execInternal("getprop $qProp 2>&1")
        val afterVal = afterRes.stdout.trim()
        val ok = writeRes.isSuccess && afterVal.equals(cleanReq, ignoreCase = true)

        val err = when {
            ok -> ""
            !writeRes.isSuccess -> "${writeRes.combinedError} (exit=${writeRes.exitCode})"
            else -> "Prop readback mismatch: expected "$cleanReq", got "$afterVal""
        }

        val result = SafeWriteResult(
            path = "prop:$cleanProp",
            kind = TargetKind.PROP,
            requested = cleanReq,
            exists = true,
            permissions = "prop",
            writable = true,
            before = beforeVal,
            after = afterVal,
            exitCode = writeRes.exitCode,
            ok = ok,
            stderr = err
        )
        recordWriteResult(result)
        log(if (ok) LogType.SUCCESS else LogType.ERROR, result.toStructuredLog())
        result
    }

    /**
     * Safe write helper for Android Settings (`settings put` + `settings get` readback).
     */
    suspend fun safeWriteSetting(
        namespace: String,
        key: String,
        requested: String,
        readbackDelayMs: Long = 200L
    ): SafeWriteResult = withContext(Dispatchers.IO) {
        val ns = namespace.trim()
        val cleanKey = key.trim()
        val cleanReq = requested.trim()
        val qNs = shellSingleQuote(ns)
        val qKey = shellSingleQuote(cleanKey)
        val qVal = shellSingleQuote(cleanReq)

        val beforeRes = execInternal("settings get $qNs $qKey 2>&1")
        val beforeVal = beforeRes.stdout.trim()

        log(LogType.COMMAND, "settings put $ns $cleanKey "$cleanReq" (before="$beforeVal")")
        val writeRes = execInternal("settings put $qNs $qKey $qVal")
        if (readbackDelayMs > 0) delay(readbackDelayMs)

        val afterRes = execInternal("settings get $qNs $qKey 2>&1")
        val afterVal = afterRes.stdout.trim()
        val ok = writeRes.isSuccess && afterVal.equals(cleanReq, ignoreCase = true)

        val err = when {
            ok -> ""
            !writeRes.isSuccess -> "${writeRes.combinedError} (exit=${writeRes.exitCode})"
            else -> "Setting readback mismatch: expected "$cleanReq", got "$afterVal""
        }

        val result = SafeWriteResult(
            path = "settings:$ns/$cleanKey",
            kind = TargetKind.SETTING,
            requested = cleanReq,
            exists = true,
            permissions = "settings",
            writable = true,
            before = beforeVal,
            after = afterVal,
            exitCode = writeRes.exitCode,
            ok = ok,
            stderr = err
        )
        recordWriteResult(result)
        log(if (ok) LogType.SUCCESS else LogType.ERROR, result.toStructuredLog())
        result
    }

    suspend fun exec(command: String): CommandResult = withContext(Dispatchers.IO) {
        log(LogType.COMMAND, command)
        val res = execInternal(command)
        if (res.stdout.isNotBlank()) {
            for (line in res.stdout.trim().lines()) {
                log(LogType.INFO, line)
            }
        }
        if (res.stderr.isNotBlank()) {
            for (line in res.stderr.trim().lines()) {
                log(if (res.isSuccess) LogType.WARNING else LogType.ERROR, line)
            }
        } else if (!res.isSuccess) {
            log(LogType.ERROR, "Command failed with exit code ${res.exitCode} (empty stderr)")
        }
        res
    }

    suspend fun execSilent(command: String): CommandResult = withContext(Dispatchers.IO) {
        execInternal(command)
    }

    private fun execInternal(command: String): CommandResult {
        return try {
            val process = ProcessBuilder("su", "-c", command).start()
            var stdout = ""
            var stderr = ""
            val outThread = Thread {
                stdout = runCatching {
                    process.inputStream.bufferedReader().use(BufferedReader::readText)
                }.getOrDefault("")
            }
            val errThread = Thread {
                stderr = runCatching {
                    process.errorStream.bufferedReader().use(BufferedReader::readText)
                }.getOrDefault("")
            }
            outThread.start()
            errThread.start()
            val exitCode = process.waitFor()
            outThread.join(3000)
            errThread.join(3000)
            CommandResult(exitCode, stdout, stderr, command)
        } catch (e: Exception) {
            execInteractive(command)
        }
    }

    private fun execInteractive(command: String): CommandResult {
        return try {
            val process = ProcessBuilder("su").redirectErrorStream(false).start()
            DataOutputStream(process.outputStream).use { os ->
                os.writeBytes("$command
")
                os.writeBytes("exit
")
                os.flush()
            }
            var stdout = ""
            var stderr = ""
            val outThread = Thread {
                stdout = runCatching {
                    process.inputStream.bufferedReader().use(BufferedReader::readText)
                }.getOrDefault("")
            }
            val errThread = Thread {
                stderr = runCatching {
                    process.errorStream.bufferedReader().use(BufferedReader::readText)
                }.getOrDefault("")
            }
            outThread.start()
            errThread.start()
            val exitCode = process.waitFor()
            outThread.join(3000)
            errThread.join(3000)
            CommandResult(exitCode, stdout, stderr, command)
        } catch (e: Exception) {
            CommandResult(-1, "", e.message ?: "Execution failed", command)
        }
    }
}
