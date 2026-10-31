package com.forgebuild.otgenabler

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class RecordedChange(
    val kind: TargetKind,
    val path: String,
    val restoreValue: String,
    val appliedValue: String
)

class OtgController(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("otg_enabler_prefs", Context.MODE_PRIVATE)
    private val scanner = OtgScanner(appContext)
    private val usbMonitor = UsbMonitor(appContext)

    private val _verificationState = MutableStateFlow(loadInitialVerificationState())
    val verificationState: StateFlow<OtgVerificationState> = _verificationState.asStateFlow()

    private val _isOtgEnabled = MutableStateFlow(
        _verificationState.value == OtgVerificationState.VERIFIED ||
            _verificationState.value == OtgVerificationState.PARTIAL
    )
    val isOtgEnabled: StateFlow<Boolean> = _isOtgEnabled.asStateFlow()

    private val _evaluation = MutableStateFlow(defaultDisabledEvaluation(_verificationState.value))
    val evaluation: StateFlow<ApplyEvaluation> = _evaluation.asStateFlow()

    private val _selectedStrategy = MutableStateFlow(
        OtgStrategy.fromId(prefs.getString("strategy_id", OtgStrategy.UNIVERSAL_AUTO.id) ?: OtgStrategy.UNIVERSAL_AUTO.id)
    )
    val selectedStrategy: StateFlow<OtgStrategy> = _selectedStrategy.asStateFlow()

    private val _lastAppliedNodes = MutableStateFlow<List<String>>(emptyList())
    val lastAppliedNodes: StateFlow<List<String>> = _lastAppliedNodes.asStateFlow()

    val discoveredNodes: StateFlow<List<DiscoveredNode>> = scanner.discoveredNodes
    val isScanning: StateFlow<Boolean> = scanner.isScanning

    private val _autoEnableOnBoot = MutableStateFlow(prefs.getBoolean("auto_enable_on_boot", false))
    val autoEnableOnBoot: StateFlow<Boolean> = _autoEnableOnBoot.asStateFlow()

    private val _tryWithSelinuxPermissive = MutableStateFlow(prefs.getBoolean("try_selinux_permissive", false))
    val tryWithSelinuxPermissive: StateFlow<Boolean> = _tryWithSelinuxPermissive.asStateFlow()

    private val _extraLegacyTweaks = MutableStateFlow(prefs.getBoolean("extra_legacy_tweaks", false))
    val extraLegacyTweaks: StateFlow<Boolean> = _extraLegacyTweaks.asStateFlow()

    private val _lastDmesgBefore = MutableStateFlow("")
    val lastDmesgBefore: StateFlow<String> = _lastDmesgBefore.asStateFlow()

    private val _lastDmesgAfter = MutableStateFlow("")
    val lastDmesgAfter: StateFlow<String> = _lastDmesgAfter.asStateFlow()

    private val _lastDmesgDiff = MutableStateFlow<List<String>>(emptyList())
    val lastDmesgDiff: StateFlow<List<String>> = _lastDmesgDiff.asStateFlow()

    private fun loadInitialVerificationState(): OtgVerificationState {
        val saved = prefs.getString("verification_state", null)
        return if (saved != null) {
            runCatching { OtgVerificationState.valueOf(saved) }.getOrDefault(OtgVerificationState.DISABLED)
        } else {
            OtgVerificationState.DISABLED
        }
    }

    private fun defaultDisabledEvaluation(state: OtgVerificationState): ApplyEvaluation {
        return when (state) {
            OtgVerificationState.VERIFIED -> ApplyEvaluation(
                state = OtgVerificationState.VERIFIED,
                appliedCount = 0,
                hostRoleConfirmed = true,
                vbusConfirmed = true,
                enumeratedDevicesCount = 0,
                headline = "USB OTG Verified",
                statusBadge = "VERIFIED HOST + 5V VBUS",
                subtitle = "Supplying 5V VBUS power (verified in previous session)",
                detailMessage = "Tap Refresh or re-run Enable to re-verify live kernel readbacks."
            )
            OtgVerificationState.PARTIAL -> ApplyEvaluation(
                state = OtgVerificationState.PARTIAL,
                appliedCount = 0,
                hostRoleConfirmed = true,
                vbusConfirmed = false,
                enumeratedDevicesCount = 0,
                headline = "Host Role Set (Partial)",
                statusBadge = "PARTIAL — VBUS UNCONFIRMED",
                subtitle = "Host role set, VBUS not confirmed",
                detailMessage = "Previous run set host role without confirmed 5V VBUS."
            )
            else -> ApplyEvaluation(
                state = OtgVerificationState.DISABLED,
                appliedCount = 0,
                hostRoleConfirmed = false,
                vbusConfirmed = false,
                enumeratedDevicesCount = 0,
                headline = "USB OTG Disabled",
                statusBadge = "PERIPHERAL / IDLE",
                subtitle = "Tap to probe sysfs, verify host role, and enable 5V VBUS",
                detailMessage = ""
            )
        }
    }

    fun setStrategy(strategy: OtgStrategy) {
        _selectedStrategy.value = strategy
        prefs.edit().putString("strategy_id", strategy.id).apply()
        RootCommander.log(LogType.INFO, "Strategy selected: ${strategy.title}")
    }

    fun setAutoEnableOnBoot(enabled: Boolean) {
        _autoEnableOnBoot.value = enabled
        prefs.edit().putBoolean("auto_enable_on_boot", enabled).apply()
        RootCommander.log(LogType.INFO, "Auto-enable on boot set to: $enabled")
    }

    fun setTryWithSelinuxPermissive(enabled: Boolean) {
        _tryWithSelinuxPermissive.value = enabled
        prefs.edit().putBoolean("try_selinux_permissive", enabled).apply()
        RootCommander.log(LogType.INFO, "Try with SELinux permissive set to: $enabled")
    }

    fun setExtraLegacyTweaks(enabled: Boolean) {
        _extraLegacyTweaks.value = enabled
        prefs.edit().putBoolean("extra_legacy_tweaks", enabled).apply()
        RootCommander.log(LogType.INFO, "Extra legacy OEM tweaks set to: $enabled")
    }

    suspend fun scanSysfsNodes(): List<DiscoveredNode> = scanner.scan()

    suspend fun captureFilteredDmesg(tailLines: Int = 120): String = withContext(Dispatchers.IO) {
        val cmd = "dmesg 2>/dev/null | grep -i -E 'tcpc|typec|usb|otg|vbus|mtu3|musb|xhci' | tail -n $tailLines"
        val res = RootCommander.execSilent(cmd)
        if (res.isSuccess) res.stdout.trim() else ""
    }

    /**
     * Ordered verified OTG apply flow (Section 6.3):
     * 1. Capture pre-attempt filtered dmesg & run live sysfs scan.
     * 2. Apply ordered steps for the selected strategy via RootCommander.safeWriteSysfs,
     *    recording pre-write values and stopping early if both Host Role + VBUS are verified.
     * 3. Capture post-attempt filtered dmesg and log the diff.
     * 4. Poll /sys/bus/usb/devices for non-root-hub accessory enumeration.
     * 5. Report honest VERIFIED / PARTIAL / FAILED state.
     */
    suspend fun enableOtg(strategy: OtgStrategy = _selectedStrategy.value): ApplyEvaluation = withContext(Dispatchers.IO) {
        RootCommander.clearWriteSession()
        RootCommander.log(
            LogType.INFO,
            ">>> ENABLE OTG START (Strategy: ${strategy.title} | ${Build.MANUFACTURER} ${Build.MODEL} ${Build.HARDWARE}) <<<"
        )

        // Optional SELinux permissive ONLY when user explicitly opted in (Section 6.4)
        if (_tryWithSelinuxPermissive.value) {
            val currentMode = RootCommander.getSELinuxStatus()
            if (currentMode.equals("Enforcing", ignoreCase = true)) {
                val seRes = RootCommander.setSELinuxMode(permissive = true)
                if (seRes.isSuccess) {
                    prefs.edit()
                        .putBoolean("selinux_changed_by_app", true)
                        .putString("selinux_original_mode", currentMode)
                        .apply()
                }
            }
        }

        // Capture dmesg BEFORE
        val dmesgBefore = captureFilteredDmesg(tailLines = 120)
        _lastDmesgBefore.value = dmesgBefore

        // Scan live sysfs nodes and feed directly into apply
        val scanned = scanner.scan()
        val scanByPath = scanned.associateBy { it.path }
        val attemptedRealPaths = mutableSetOf<String>()
        val sessionWrites = mutableListOf<SafeWriteResult>()
        val recordedChanges = loadRecordedChanges().toMutableList()

        suspend fun writeCandidateIfWritable(path: String, value: String): SafeWriteResult? {
            val clean = path.trim()
            if (SysfsReadbackParser.shouldExcludePath(clean)) return null

            val scannedNode = scanByPath[clean] ?: scanned.firstOrNull { it.realPath == clean }
            val canonical = scannedNode?.realPath ?: clean
            if (canonical in attemptedRealPaths) return null

            if (scannedNode != null) {
                if (!scannedNode.isControllable) {
                    // Never write to read-only / info-only nodes (e.g. extcon0/state -r--r--r--)
                    return null
                }
            } else {
                // Fallback path not in scan: check existence + writability quietly before attempting
                val qPath = RootCommander.shellSingleQuote(clean)
                val probe = RootCommander.execSilent("[ -f $qPath ] && [ -w $qPath ] && echo RW || ([ -e $qPath ] && echo RO || echo NO)")
                val tag = probe.stdout.trim()
                if (tag != "RW") {
                    return null
                }
            }

            attemptedRealPaths.add(canonical)
            val res = RootCommander.safeWriteSysfs(clean, value)
            sessionWrites.add(res)

            if (res.ok) {
                val restoreVal = inferRestoreValue(clean, res.before, value)
                if (restoreVal.isNotBlank() && recordedChanges.none { it.path == clean }) {
                    recordedChanges.add(
                        RecordedChange(
                            kind = TargetKind.SYSFS,
                            path = clean,
                            restoreValue = restoreVal,
                            appliedValue = value
                        )
                    )
                }
            }
            return res
        }

        suspend fun runStepRoleSwitches() {
            // 1a. Scanned writable ROLE_SWITCH nodes
            for (node in scanned.filter { it.isControllable && it.category == NodeCategory.ROLE_SWITCH }) {
                val targetVal = if (node.path.endsWith("host_mode")) "1" else "host"
                writeCandidateIfWritable(node.path, targetVal)
            }
            // 1b. Known MTK / generic USB role switch fallbacks
            val roleFallbacks = listOf(
                "/sys/class/usb_role/mt_usb-role-switch/role" to "host",
                "/sys/class/usb_role/musb-hdrc-role-switch/role" to "host",
                "/sys/class/usb_role/11200000.usb-role-switch/role" to "host",
                "/sys/devices/platform/mt_usb/otg_mode" to "host",
                "/sys/devices/platform/mt_usb/mode" to "host",
                "/sys/devices/platform/musb-hdrc/mode" to "host",
                "/sys/devices/platform/11200000.usb/musb-hdrc/mode" to "host"
            )
            for ((p, v) in roleFallbacks) {
                writeCandidateIfWritable(p, v)
            }
        }

        suspend fun runStepTypecAndTcpc() {
            val typecPowerNodes = scanned.filter { it.isControllable && it.category == NodeCategory.TYPEC_POWER_ROLE }
            val typecDataNodes = scanned.filter { it.isControllable && it.category == NodeCategory.TYPEC_DATA_ROLE }

            // Configure port_type=dual and preferred_role=source first if writable
            for (node in typecPowerNodes.filter { it.path.endsWith("/port_type") }) {
                writeCandidateIfWritable(node.path, "dual")
            }
            writeCandidateIfWritable("/sys/class/typec/port0/port_type", "dual")

            for (node in typecPowerNodes.filter { it.path.endsWith("/preferred_role") }) {
                writeCandidateIfWritable(node.path, "source")
            }
            writeCandidateIfWritable("/sys/class/typec/port0/preferred_role", "source")

            // Set data_role=host (or dual_role mode=dfp)
            for (node in typecDataNodes) {
                val v = if (node.path.contains("/dual_role_usb/") && node.path.endsWith("/mode")) "dfp" else "host"
                writeCandidateIfWritable(node.path, v)
            }
            writeCandidateIfWritable("/sys/class/typec/port0/data_role", "host")

            // Set power_role=source
            for (node in typecPowerNodes.filter { it.path.endsWith("/power_role") }) {
                writeCandidateIfWritable(node.path, "source")
            }
            writeCandidateIfWritable("/sys/class/typec/port0/power_role", "source")

            // Try rt-pd-manager / tcpc equivalents discovered by scanner
            for (node in scanned.filter {
                it.isControllable && (it.path.contains("rt-pd", ignoreCase = true) || it.path.contains("tcpc", ignoreCase = true))
            }) {
                val fn = node.path.substringAfterLast('/').lowercase()
                val v = when {
                    fn == "data_role" || fn == "role" -> "host"
                    fn == "power_role" -> "source"
                    fn == "port_type" -> "dual"
                    else -> desiredEnableValueForChargerOrRegulator(node)
                }
                if (v != null) writeCandidateIfWritable(node.path, v)
            }
        }

        suspend fun runStepVbusBoostAndRegulators() {
            // 3a. Scanned CHARGER_OTG writable control nodes (excluding passive telemetry attributes)
            for (node in scanned.filter { it.isControllable && it.category == NodeCategory.CHARGER_OTG }) {
                val desired = desiredEnableValueForChargerOrRegulator(node) ?: continue
                writeCandidateIfWritable(node.path, desired)
            }

            // 3b. Known MTK / Charger / PowerSupply OTG boost fallbacks
            val vbusFallbacks = listOf(
                "/sys/class/power_supply/usb/otg_switch" to "1",
                "/sys/class/power_supply/otg/online" to "1",
                "/sys/class/power_supply/charger/otg_enable" to "1",
                "/sys/class/power_supply/charger/otg_switch" to "1",
                "/sys/class/power_supply/mtk-master-charger/otg_enable" to "1",
                "/sys/devices/platform/charger/otg_switch" to "1",
                "/sys/devices/platform/charger/otg_enable" to "1",
                "/sys/devices/platform/mt_charger/otg_enable" to "1",
                "/sys/devices/platform/mt_charger/otg_switch" to "1",
                "/sys/devices/platform/tran_battery/OTG_CTL" to "1",
                "/sys/devices/platform/odm/odm:tran_battery/OTG_CTL" to "1"
            )
            for ((p, v) in vbusFallbacks) {
                writeCandidateIfWritable(p, v)
            }

            // 3c. Scanned VBUS_OTG_REGULATOR writable nodes (usb_otg_vbus / otg-vbus)
            for (node in scanned.filter { it.isControllable && it.category == NodeCategory.VBUS_OTG_REGULATOR }) {
                val desired = desiredEnableValueForChargerOrRegulator(node) ?: continue
                writeCandidateIfWritable(node.path, desired)
            }
        }

        suspend fun isFullSuccessAlready(): Boolean {
            val check = verifyLiveHostAndVbus(sessionWrites, emptyList(), emptyList())
            return check.first && check.second
        }

        // Execute strategy steps in verified order, stopping early on full verified success
        when (strategy) {
            OtgStrategy.UNIVERSAL_AUTO, OtgStrategy.MTK_TCPC_VBUS -> {
                runStepRoleSwitches()
                if (!isFullSuccessAlready()) {
                    runStepTypecAndTcpc()
                }
                if (!isFullSuccessAlready()) {
                    runStepVbusBoostAndRegulators()
                }
            }
            OtgStrategy.USB_ROLE_TYPEC -> {
                runStepRoleSwitches()
                if (!isFullSuccessAlready()) {
                    runStepTypecAndTcpc()
                }
            }
            OtgStrategy.TRANSSION_BATTERY -> {
                val transsionPaths = buildList {
                    scanned.filter {
                        it.isControllable && (it.path.contains("tran_battery", ignoreCase = true) ||
                            it.path.contains("otg_ctl", ignoreCase = true))
                    }.forEach { add(it.path) }
                    add("/sys/devices/platform/tran_battery/OTG_CTL")
                    add("/sys/devices/platform/tran_battery/otg_ctl")
                    add("/sys/class/power_supply/tran_battery/otg_ctl")
                    add("/sys/devices/platform/tran_battery/otg_en")
                    add("/sys/devices/platform/tran_battery/otg_switch")
                    add("/sys/devices/platform/odm/odm:tran_battery/OTG_CTL")
                }.distinct()
                for (p in transsionPaths) {
                    writeCandidateIfWritable(p, "1")
                }
                if (sessionWrites.any { it.ok }) {
                    runStepRoleSwitches()
                }
            }
            OtgStrategy.DUAL_ROLE_DFP -> {
                for (node in scanned.filter { it.isControllable && it.path.contains("/dual_role_usb/") }) {
                    val fn = node.path.substringAfterLast('/')
                    val v = when (fn) {
                        "mode" -> "dfp"
                        "data_role" -> "host"
                        "power_role" -> "source"
                        else -> null
                    }
                    if (v != null) writeCandidateIfWritable(node.path, v)
                }
                if (sessionWrites.any { it.ok } && !isFullSuccessAlready()) {
                    runStepVbusBoostAndRegulators()
                }
            }
            OtgStrategy.QUALCOMM_DWC3 -> {
                val qcomNodes = scanned.filter {
                    it.isControllable && (it.path.contains("dwc3") || it.path.contains("msm_otg") || it.path.contains("ci_hdrc"))
                }
                for (node in qcomNodes) {
                    val v = if (node.path.endsWith("host_mode")) "1" else "host"
                    writeCandidateIfWritable(node.path, v)
                }
                if (!isFullSuccessAlready()) {
                    runStepTypecAndTcpc()
                }
                if (!isFullSuccessAlready()) {
                    runStepVbusBoostAndRegulators()
                }
            }
            OtgStrategy.EXTCON_CABLE -> {
                for (node in scanned.filter { it.isControllable && it.category == NodeCategory.EXTCON }) {
                    writeCandidateIfWritable(node.path, "USB-HOST=1")
                }
            }
            OtgStrategy.AGGRESSIVE_ALL -> {
                runStepRoleSwitches()
                runStepTypecAndTcpc()
                runStepVbusBoostAndRegulators()
                for (node in scanned.filter { it.isControllable && it.category == NodeCategory.EXTCON }) {
                    writeCandidateIfWritable(node.path, "USB-HOST=1")
                }
            }
        }

        // Optional Extra Legacy OEM Tweaks (OFF by default — Section 5 #4 & 6.7)
        if (_extraLegacyTweaks.value) {
            RootCommander.log(LogType.INFO, "Applying optional Extra Legacy OEM tweaks (user-enabled)...")
            val prop1 = RootCommander.safeWriteProp("persist.vendor.otg.support", "1")
            sessionWrites.add(prop1)
            if (prop1.ok && recordedChanges.none { it.path == "persist.vendor.otg.support" && it.kind == TargetKind.PROP }) {
                recordedChanges.add(RecordedChange(TargetKind.PROP, "persist.vendor.otg.support", prop1.before.ifBlank { "0" }, "1"))
            }
            val set1 = RootCommander.safeWriteSetting("global", "otg_enabled", "1")
            sessionWrites.add(set1)
            if (set1.ok && recordedChanges.none { it.path == "global/otg_enabled" && it.kind == TargetKind.SETTING }) {
                recordedChanges.add(RecordedChange(TargetKind.SETTING, "global/otg_enabled", set1.before.ifBlank { "0" }, "1"))
            }
        }

        saveRecordedChanges(recordedChanges)

        // Step 4: Capture dmesg AFTER and log diff in Root Logs
        delay(350L)
        val dmesgAfter = captureFilteredDmesg(tailLines = 140)
        _lastDmesgAfter.value = dmesgAfter
        val diffLines = DmesgDiffHelper.computeNewLines(dmesgBefore, dmesgAfter)
        _lastDmesgDiff.value = diffLines

        if (diffLines.isEmpty()) {
            RootCommander.log(LogType.INFO, "Kernel dmesg diff: 0 new matching USB/TCPC/OTG/VBUS lines.")
        } else {
            RootCommander.log(LogType.INFO, "--- Kernel dmesg diff (+${diffLines.size} lines) ---")
            for (line in diffLines.takeLast(25)) {
                RootCommander.log(LogType.INFO, "dmesg: $line")
            }
        }

        // Step 5: Wait up to ~2.5s and check /sys/bus/usb/devices for non-root-hub accessory
        RootCommander.log(LogType.INFO, "Waiting for USB bus enumeration (/sys/bus/usb/devices, ignoring 1d6b root hubs)...")
        var realDevices: List<ConnectedUsbDevice> = emptyList()
        for (attempt in 1..5) {
            delay(500L)
            realDevices = usbMonitor.refresh()
            if (realDevices.isNotEmpty()) break
        }

        // Refresh scanner so UI shows live post-write values
        val postScan = scanner.scan()
        val (hostOk, vbusOk, evidences) = verifyLiveHostAndVbusFull(sessionWrites, postScan, diffLines, realDevices)

        val resultEval = OtgStateEvaluator.evaluate(
            strategyTitle = strategy.title,
            writeResults = sessionWrites,
            hostRoleConfirmed = hostOk,
            vbusConfirmed = vbusOk,
            vbusEvidence = evidences.second,
            hostEvidence = evidences.first,
            enumeratedDevices = realDevices
        )

        _lastAppliedNodes.value = sessionWrites.filter { it.ok }.map { "${it.path} = ${it.activeAfterValue}" }
        _verificationState.value = resultEval.state
        _evaluation.value = resultEval
        val activeOrPartial = resultEval.state == OtgVerificationState.VERIFIED ||
            resultEval.state == OtgVerificationState.PARTIAL
        _isOtgEnabled.value = activeOrPartial

        prefs.edit()
            .putBoolean("is_otg_enabled", activeOrPartial)
            .putString("verification_state", resultEval.state.name)
            .apply()

        val logLevel = when (resultEval.state) {
            OtgVerificationState.VERIFIED -> LogType.SUCCESS
            OtgVerificationState.PARTIAL -> LogType.WARNING
            else -> LogType.ERROR
        }
        RootCommander.log(logLevel, "RESULT [${resultEval.state}]: ${resultEval.subtitle} — ${resultEval.detailMessage}")
        resultEval
    }

    /**
     * Restores ONLY the nodes recorded as changed during enable, verifying each restore with readback.
     * Never touches unverified or read-only nodes such as extcon0/state (Section 6.4).
     */
    suspend fun disableOtg(): Boolean = withContext(Dispatchers.IO) {
        RootCommander.clearWriteSession()
        RootCommander.log(LogType.INFO, ">>> DISABLE OTG / RESTORE RECORDED VALUES <<<")

        val changes = loadRecordedChanges()
        if (changes.isEmpty()) {
            RootCommander.log(LogType.INFO, "No recorded node changes to restore (skipping unverified paths).")
        } else {
            for (change in changes.asReversed()) {
                when (change.kind) {
                    TargetKind.SYSFS -> {
                        val res = RootCommander.safeWriteSysfs(change.path, change.restoreValue)
                        if (!res.ok && change.restoreValue == "none" && change.path.endsWith("/role")) {
                            // Some usb_role drivers only accept "device" rather than "none" when leaving "host"
                            RootCommander.safeWriteSysfs(change.path, "device")
                        }
                    }
                    TargetKind.PROP -> {
                        RootCommander.safeWriteProp(change.path, change.restoreValue)
                    }
                    TargetKind.SETTING -> {
                        val ns = change.path.substringBefore('/')
                        val key = change.path.substringAfter('/')
                        RootCommander.safeWriteSetting(ns, key, change.restoreValue)
                    }
                }
            }
        }

        clearRecordedChanges()

        // Restore SELinux ONLY if the app changed it via the explicit user option
        if (prefs.getBoolean("selinux_changed_by_app", false)) {
            val orig = prefs.getString("selinux_original_mode", "Enforcing") ?: "Enforcing"
            if (orig.equals("Enforcing", ignoreCase = true)) {
                RootCommander.setSELinuxMode(permissive = false)
            }
            prefs.edit().putBoolean("selinux_changed_by_app", false).apply()
        }

        scanner.scan()
        usbMonitor.refresh()

        _lastAppliedNodes.value = emptyList()
        _verificationState.value = OtgVerificationState.DISABLED
        _isOtgEnabled.value = false
        _evaluation.value = defaultDisabledEvaluation(OtgVerificationState.DISABLED)

        prefs.edit()
            .putBoolean("is_otg_enabled", false)
            .putString("verification_state", OtgVerificationState.DISABLED.name)
            .apply()

        RootCommander.log(LogType.SUCCESS, "OTG restore complete.")
        true
    }

    suspend fun writeCustomNode(path: String, value: String): SafeWriteResult = withContext(Dispatchers.IO) {
        val res = RootCommander.safeWriteSysfs(path, value)
        if (res.ok) {
            val recorded = loadRecordedChanges().toMutableList()
            val restoreVal = inferRestoreValue(path, res.before, value)
            if (restoreVal.isNotBlank() && recorded.none { it.path == path }) {
                recorded.add(RecordedChange(TargetKind.SYSFS, path, restoreVal, value))
                saveRecordedChanges(recorded)
            }
        }
        res
    }

    private fun desiredEnableValueForChargerOrRegulator(node: DiscoveredNode): String? {
        val fn = node.path.substringAfterLast('/').lowercase()
        // Never attempt to echo into readback-only telemetry counters even if kernel gave odd perms
        if (fn in setOf("voltage_now", "current_now", "temp", "capacity", "type", "usb_type", "present", "microvolts", "num_users", "status")) {
            return null
        }
        val active = node.activeValue.lowercase()
        return when {
            fn == "state" && node.category == NodeCategory.VBUS_OTG_REGULATOR -> {
                if (active == "disabled" || active == "enabled") "enabled" else "1"
            }
            active in setOf("disabled", "enabled") -> "enabled"
            active in setOf("off", "on") -> "on"
            active in setOf("false", "true") -> "true"
            fn.contains("mode") -> "host"
            else -> "1"
        }
    }

    private fun inferRestoreValue(path: String, rawBefore: String, appliedValue: String): String {
        val activeBefore = SysfsReadbackParser.extractActiveValue(rawBefore)
        if (activeBefore.isNotBlank() && !SysfsReadbackParser.tokensEquivalent(appliedValue, activeBefore, path)) {
            return activeBefore
        }
        val fn = path.substringAfterLast('/').lowercase()
        return when {
            fn == "role" || fn == "data_role" -> "device"
            fn == "power_role" -> "sink"
            fn == "mode" && path.contains("dual_role") -> "ufp"
            fn == "mode" || fn == "otg_mode" -> "peripheral"
            appliedValue == "enabled" -> "disabled"
            appliedValue == "on" -> "off"
            appliedValue == "USB-HOST=1" -> "USB-HOST=0"
            appliedValue == "1" -> "0"
            else -> activeBefore
        }
    }

    private fun verifyLiveHostAndVbus(
        writes: List<SafeWriteResult>,
        postScan: List<DiscoveredNode>,
        diffLines: List<String>
    ): Pair<Boolean, Boolean> {
        val (h, v, _) = verifyLiveHostAndVbusFull(writes, postScan, diffLines, emptyList())
        return h to v
    }

    private fun verifyLiveHostAndVbusFull(
        writes: List<SafeWriteResult>,
        postScan: List<DiscoveredNode>,
        diffLines: List<String>,
        realDevices: List<ConnectedUsbDevice>
    ): Triple<Boolean, Boolean, Pair<String, String>> {
        var hostEvidence = ""
        var vbusEvidence = ""

        // 1. Host Role verification: check verified writes + postScan active values
        val hostWrite = writes.firstOrNull { w ->
            w.ok && (w.path.contains("/usb_role/") ||
                w.path.endsWith("/data_role") ||
                w.path.endsWith("/otg_mode") ||
                (w.path.endsWith("/mode") && !w.path.contains("power"))) &&
                SysfsReadbackParser.tokensEquivalent("host", w.activeAfterValue, w.path)
        }
        if (hostWrite != null) {
            hostEvidence = "${hostWrite.path.substringAfterLast('/')}==${hostWrite.activeAfterValue}"
        } else {
            val scanHost = postScan.firstOrNull { n ->
                (n.category == NodeCategory.ROLE_SWITCH || n.category == NodeCategory.TYPEC_DATA_ROLE) &&
                    SysfsReadbackParser.tokensEquivalent("host", n.activeValue, n.path)
            }
            if (scanHost != null) {
                hostEvidence = "${scanHost.path}==${scanHost.activeValue}"
            }
        }

        // 2. VBUS 5V verification (strictly requires real power/VBUS confirmation, NEVER port_type=dual alone!)
        // 2a. Actual power_role == source
        val powerRoleWrite = writes.firstOrNull { w ->
            w.ok && w.path.endsWith("/power_role") &&
                SysfsReadbackParser.tokensEquivalent("source", w.activeAfterValue, w.path)
        }
        val powerRoleScan = postScan.firstOrNull { n ->
            n.path.endsWith("/power_role") &&
                SysfsReadbackParser.tokensEquivalent("source", n.activeValue, n.path)
        }

        // 2b. Dedicated charger OTG / VBUS regulator write verified via readback
        val vbusSwitchWrite = writes.firstOrNull { w ->
            val p = w.path.lowercase()
            val fn = p.substringAfterLast('/')
            w.ok && w.kind == TargetKind.SYSFS &&
                fn !in setOf("role", "data_role", "port_type", "preferred_role", "mode", "otg_mode") &&
                (p.contains("otg") || p.contains("vbus") || p.contains("boost") || p.contains("regulator")) &&
                SysfsReadbackParser.tokensEquivalent("1", w.activeAfterValue, w.path)
        }

        // 2c. Read-only regulator / power_supply OTG telemetry showing enabled/online in post-scan
        val regulatorEnabledScan = postScan.firstOrNull { n ->
            val p = n.path.lowercase()
            ((p.contains("/regulator/") && p.endsWith("/state") && n.activeValue.equals("enabled", ignoreCase = true)) ||
                (p.contains("/power_supply/otg/online") && n.activeValue == "1"))
        }

        // 2d. Kernel dmesg diff showing explicit VBUS boost / source activation
        val dmesgVbus = DmesgDiffHelper.hasConfirmedVbusInDmesg(diffLines)

        vbusEvidence = when {
            powerRoleWrite != null -> "power_role==${powerRoleWrite.activeAfterValue}"
            powerRoleScan != null -> "${powerRoleScan.path}==${powerRoleScan.activeValue}"
            vbusSwitchWrite != null -> "${vbusSwitchWrite.path}==${vbusSwitchWrite.activeAfterValue}"
            regulatorEnabledScan != null -> "${regulatorEnabledScan.path} (${regulatorEnabledScan.detailHint})==${regulatorEnabledScan.activeValue}"
            dmesgVbus -> "kernel dmesg VBUS source event"
            realDevices.isNotEmpty() -> "real USB accessory enumerated (${realDevices.first().displayTitle})"
            else -> ""
        }

        if (hostEvidence.isBlank() && realDevices.isNotEmpty()) {
            hostEvidence = "USB host enumerated ${realDevices.first().displayTitle}"
        }

        return Triple(hostEvidence.isNotBlank(), vbusEvidence.isNotBlank(), hostEvidence to vbusEvidence)
    }

    private fun saveRecordedChanges(list: List<RecordedChange>) {
        val arr = JSONArray()
        for (item in list) {
            arr.put(
                JSONObject()
                    .put("kind", item.kind.name)
                    .put("path", item.path)
                    .put("restoreValue", item.restoreValue)
                    .put("appliedValue", item.appliedValue)
            )
        }
        prefs.edit().putString("recorded_changes_v2", arr.toString()).apply()
    }

    private fun loadRecordedChanges(): List<RecordedChange> {
        val raw = prefs.getString("recorded_changes_v2", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                RecordedChange(
                    kind = runCatching { TargetKind.valueOf(o.optString("kind", "SYSFS")) }.getOrDefault(TargetKind.SYSFS),
                    path = o.getString("path"),
                    restoreValue = o.getString("restoreValue"),
                    appliedValue = o.optString("appliedValue", "")
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun clearRecordedChanges() {
        prefs.edit().remove("recorded_changes_v2").apply()
    }
}
