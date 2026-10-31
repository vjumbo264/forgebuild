package com.forgebuild.otgenabler

import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
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
}

data class LogEntry(
    val id: Long = System.currentTimeMillis() + (0..999).random(),
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

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun log(type: LogType, message: String) {
        val entry = LogEntry(
            timestamp = timeFormat.format(Date()),
            type = type,
            message = message
        )
        val current = _logs.value
        _logs.value = (current + entry).takeLast(400)
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    suspend fun checkRoot(forceRefresh: Boolean = false): RootStatus = withContext(Dispatchers.IO) {
        if (!forceRefresh && _rootStatus.value != RootStatus.NOT_CHECKED) {
            return@withContext _rootStatus.value
        }

        log(LogType.INFO, "Scanning device for root privileges...")

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
        log(LogType.INFO, "Root provider: $flavor")

        val result = execInternal("id")
        val status = if (result.isSuccess && result.stdout.contains("uid=0")) {
            log(LogType.SUCCESS, "Root access granted: ${result.stdout.trim()}")
            RootStatus.GRANTED
        } else {
            val alt = execInteractive("id")
            if (alt.isSuccess && alt.stdout.contains("uid=0")) {
                log(LogType.SUCCESS, "Root access granted via interactive shell: ${alt.stdout.trim()}")
                RootStatus.GRANTED
            } else {
                if (foundBinary != null) {
                    log(LogType.ERROR, "Root permission rejected or pending prompt. Result: ${result.stderr.ifBlank { result.stdout }}")
                    RootStatus.DENIED
                } else {
                    log(LogType.WARNING, "No su binary detected on standard paths.")
                    RootStatus.NOT_ROOTED
                }
            }
        }

        _rootStatus.value = status
        status
    }

    suspend fun getSELinuxStatus(): String = withContext(Dispatchers.IO) {
        val res = execSilent("getenforce")
        if (res.isSuccess && res.stdout.isNotBlank()) res.stdout.trim() else "Unknown"
    }

    suspend fun setSELinuxPermissive(): CommandResult = withContext(Dispatchers.IO) {
        log(LogType.WARNING, "Running: setenforce 0 (SELinux Permissive)...")
        val res = exec("setenforce 0")
        if (res.isSuccess) {
            log(LogType.SUCCESS, "SELinux switched to Permissive mode")
        } else {
            log(LogType.ERROR, "Failed to switch SELinux: ${res.stderr}")
        }
        res
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
        }
        res
    }

    suspend fun execSilent(command: String): CommandResult = withContext(Dispatchers.IO) {
        execInternal(command)
    }

    private fun execInternal(command: String): CommandResult {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val stdout = process.inputStream.bufferedReader().use(BufferedReader::readText)
            val stderr = process.errorStream.bufferedReader().use(BufferedReader::readText)
            val exitCode = process.waitFor()
            CommandResult(exitCode, stdout, stderr, command)
        } catch (e: Exception) {
            execInteractive(command)
        }
    }

    private fun execInteractive(command: String): CommandResult {
        return try {
            val process = ProcessBuilder("su").redirectErrorStream(false).start()
            DataOutputStream(process.outputStream).use { os ->
                os.writeBytes("$command\n")
                os.writeBytes("exit\n")
                os.flush()
            }
            val stdout = process.inputStream.bufferedReader().use(BufferedReader::readText)
            val stderr = process.errorStream.bufferedReader().use(BufferedReader::readText)
            val exitCode = process.waitFor()
            CommandResult(exitCode, stdout, stderr, command)
        } catch (e: Exception) {
            CommandResult(-1, "", e.message ?: "Execution failed", command)
        }
    }
}
