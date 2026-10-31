package com.forgebuild.otgenabler

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class DiscoveredNode(
    val path: String,
    val currentValue: String,
    val permissions: String,
    val isWritable: Boolean,
    val candidateType: String
)

class OtgController(private val context: Context) {
    private val prefs = context.getSharedPreferences("otg_enabler_prefs", Context.MODE_PRIVATE)

    private val _isOtgEnabled = MutableStateFlow(prefs.getBoolean("is_otg_enabled", false))
    val isOtgEnabled: StateFlow<Boolean> = _isOtgEnabled.asStateFlow()

    private val _selectedStrategy = MutableStateFlow(
        OtgStrategy.fromId(prefs.getString("strategy_id", OtgStrategy.UNIVERSAL_AUTO.id) ?: OtgStrategy.UNIVERSAL_AUTO.id)
    )
    val selectedStrategy: StateFlow<OtgStrategy> = _selectedStrategy.asStateFlow()

    private val _lastAppliedNodes = MutableStateFlow<List<String>>(emptyList())
    val lastAppliedNodes: StateFlow<List<String>> = _lastAppliedNodes.asStateFlow()

    private val _discoveredNodes = MutableStateFlow<List<DiscoveredNode>>(emptyList())
    val discoveredNodes: StateFlow<List<DiscoveredNode>> = _discoveredNodes.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _autoEnableOnBoot = MutableStateFlow(prefs.getBoolean("auto_enable_on_boot", false))
    val autoEnableOnBoot: StateFlow<Boolean> = _autoEnableOnBoot.asStateFlow()

    fun setStrategy(strategy: OtgStrategy) {
        _selectedStrategy.value = strategy
        prefs.edit().putString("strategy_id", strategy.id).apply()
        RootCommander.log(LogType.INFO, "Active strategy switched to: ${strategy.title}")
    }

    fun setAutoEnableOnBoot(enabled: Boolean) {
        _autoEnableOnBoot.value = enabled
        prefs.edit().putBoolean("auto_enable_on_boot", enabled).apply()
        RootCommander.log(LogType.INFO, "Auto-enable on boot set to: $enabled")
    }

    suspend fun enableOtg(strategy: OtgStrategy = _selectedStrategy.value): Boolean = withContext(Dispatchers.IO) {
        RootCommander.log(LogType.INFO, ">>> ENABLING USB OTG HOST MODE (Strategy: ${strategy.title}) <<<")
        val touchedNodes = mutableListOf<String>()

        when (strategy) {
            OtgStrategy.TRANSSION_BATTERY -> {
                applyTranssionNodes(enable = true, touchedNodes)
            }
            OtgStrategy.MTK_POWER_SUPPLY -> {
                applyMtkNodes(enable = true, touchedNodes)
            }
            OtgStrategy.DUAL_ROLE_DFP -> {
                applyDualRoleNodes(enable = true, touchedNodes)
            }
            OtgStrategy.USB_ROLE_HOST -> {
                applyUsbRoleNodes(enable = true, touchedNodes)
            }
            OtgStrategy.TYPEC_TCPM -> {
                applyTypecNodes(enable = true, touchedNodes)
            }
            OtgStrategy.QUALCOMM_DWC3 -> {
                applyQualcommNodes(enable = true, touchedNodes)
            }
            OtgStrategy.EXTCON_CABLE -> {
                applyExtconNodes(enable = true, touchedNodes)
            }
            OtgStrategy.OEM_PROPERTIES -> {
                applyOemProperties(enable = true)
                touchedNodes.add("OEM System Properties (persist.sys.oem.otg_support)")
            }
            OtgStrategy.UNIVERSAL_AUTO -> {
                // Universal Auto: Probe device and apply matching hardware strategies + universal triggers
                RootCommander.log(LogType.INFO, "Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.HARDWARE})")
                applyUniversalAuto(enable = true, touchedNodes)
            }
            OtgStrategy.AGGRESSIVE_ALL -> {
                RootCommander.log(LogType.WARNING, "Applying ALL OTG host switches and power overrides...")
                applyTranssionNodes(enable = true, touchedNodes)
                applyMtkNodes(enable = true, touchedNodes)
                applyDualRoleNodes(enable = true, touchedNodes)
                applyUsbRoleNodes(enable = true, touchedNodes)
                applyTypecNodes(enable = true, touchedNodes)
                applyQualcommNodes(enable = true, touchedNodes)
                applyExtconNodes(enable = true, touchedNodes)
                applyOemProperties(enable = true)
                touchedNodes.add("Aggressive All Triggers")
            }
        }

        _lastAppliedNodes.value = touchedNodes
        _isOtgEnabled.value = true
        prefs.edit().putBoolean("is_otg_enabled", true).apply()
        RootCommander.log(LogType.SUCCESS, "USB OTG Enabled! Applied to ${touchedNodes.size} driver nodes.")
        true
    }

    suspend fun disableOtg(): Boolean = withContext(Dispatchers.IO) {
        RootCommander.log(LogType.INFO, ">>> DISABLING USB OTG / RESTORING DEVICE MODE <<<")
        val touchedNodes = mutableListOf<String>()

        applyTranssionNodes(enable = false, touchedNodes)
        applyMtkNodes(enable = false, touchedNodes)
        applyDualRoleNodes(enable = false, touchedNodes)
        applyUsbRoleNodes(enable = false, touchedNodes)
        applyTypecNodes(enable = false, touchedNodes)
        applyQualcommNodes(enable = false, touchedNodes)
        applyExtconNodes(enable = false, touchedNodes)
        applyOemProperties(enable = false)

        _lastAppliedNodes.value = emptyList()
        _isOtgEnabled.value = false
        prefs.edit().putBoolean("is_otg_enabled", false).apply()
        RootCommander.log(LogType.SUCCESS, "USB OTG Disabled. Restored default peripheral role.")
        true
    }

    private suspend fun applyTranssionNodes(enable: Boolean, touched: MutableList<String>) {
        val value = if (enable) "1" else "0"
        val candidates = arrayOf(
            "/sys/devices/platform/tran_battery/OTG_CTL",
            "/sys/devices/platform/tran_battery/otg_ctl",
            "/sys/class/power_supply/tran_battery/otg_ctl",
            "/sys/devices/platform/tran_battery/otg_en",
            "/sys/devices/platform/tran_battery/otg_switch"
        )
        for (node in candidates) {
            writeNodeIfExists(node, value, touched)
        }
    }

    private suspend fun applyMtkNodes(enable: Boolean, touched: MutableList<String>) {
        val numVal = if (enable) "1" else "0"
        val modeVal = if (enable) "host" else "peripheral"
        val candidates = arrayOf(
            Pair("/sys/class/power_supply/usb/otg_switch", numVal),
            Pair("/sys/devices/platform/mt_usb/otg_mode", modeVal),
            Pair("/sys/devices/platform/musb-hdrc/mode", modeVal),
            Pair("/sys/devices/platform/mt_usb/mode", modeVal)
        )
        for ((node, v) in candidates) {
            writeNodeIfExists(node, v, touched)
        }
    }

    private suspend fun applyDualRoleNodes(enable: Boolean, touched: MutableList<String>) {
        val mode = if (enable) "dfp" else "ufp"
        val power = if (enable) "source" else "sink"
        val vconn = if (enable) "1" else "0"

        // Discover dual_role instances
        val scan = RootCommander.execSilent("ls /sys/class/dual_role_usb/ 2>/dev/null")
        if (scan.isSuccess && scan.stdout.isNotBlank()) {
            for (dev in scan.stdout.trim().lines()) {
                val cleanDev = dev.trim()
                if (cleanDev.isNotBlank()) {
                    writeNodeIfExists("/sys/class/dual_role_usb/$cleanDev/mode", mode, touched)
                    writeNodeIfExists("/sys/class/dual_role_usb/$cleanDev/data_role", mode, touched)
                    writeNodeIfExists("/sys/class/dual_role_usb/$cleanDev/power_role", power, touched)
                    writeNodeIfExists("/sys/class/dual_role_usb/$cleanDev/vconn_enable", vconn, touched)
                }
            }
        }
    }

    private suspend fun applyUsbRoleNodes(enable: Boolean, touched: MutableList<String>) {
        val role = if (enable) "host" else "device"
        val scan = RootCommander.execSilent("ls /sys/class/usb_role/ 2>/dev/null")
        if (scan.isSuccess && scan.stdout.isNotBlank()) {
            for (dev in scan.stdout.trim().lines()) {
                val cleanDev = dev.trim()
                if (cleanDev.isNotBlank()) {
                    writeNodeIfExists("/sys/class/usb_role/$cleanDev/role", role, touched)
                }
            }
        }
    }

    private suspend fun applyTypecNodes(enable: Boolean, touched: MutableList<String>) {
        val dataRole = if (enable) "host" else "device"
        val powerRole = if (enable) "source" else "sink"
        writeNodeIfExists("/sys/class/typec/port0/data_role", dataRole, touched)
        writeNodeIfExists("/sys/class/typec/port0/power_role", powerRole, touched)
        writeNodeIfExists("/sys/class/typec/port0/port_type", if (enable) "dual" else "dual", touched)
    }

    private suspend fun applyQualcommNodes(enable: Boolean, touched: MutableList<String>) {
        val mode = if (enable) "host" else "peripheral"
        val num = if (enable) "1" else "0"
        val nodes = arrayOf(
            Pair("/sys/kernel/debug/usb/msm_otg/mode", mode),
            Pair("/sys/devices/platform/soc/*.dwc3/otg_mode", mode),
            Pair("/sys/devices/platform/soc/*.usb/host_mode", num)
        )
        for ((node, v) in nodes) {
            writeNodePattern(node, v, touched)
        }
    }

    private suspend fun applyExtconNodes(enable: Boolean, touched: MutableList<String>) {
        val state = if (enable) "USB-HOST=1" else "USB-HOST=0"
        val scan = RootCommander.execSilent("ls /sys/class/extcon/ 2>/dev/null")
        if (scan.isSuccess && scan.stdout.isNotBlank()) {
            for (dev in scan.stdout.trim().lines()) {
                val clean = dev.trim()
                if (clean.isNotBlank()) {
                    writeNodeIfExists("/sys/class/extcon/$clean/state", state, touched)
                }
            }
        }
    }

    private suspend fun applyOemProperties(enable: Boolean) {
        val boolStr = if (enable) "true" else "false"
        val numStr = if (enable) "1" else "0"
        RootCommander.exec("setprop persist.sys.oem.otg_support $boolStr")
        RootCommander.exec("setprop persist.sys.oppo.otg_support $boolStr")
        RootCommander.exec("setprop persist.vendor.otg.support $numStr")
        RootCommander.exec("settings put global oneplus_otg_auto_close 0")
        RootCommander.exec("settings put system otg_state $numStr")
        RootCommander.exec("settings put global otg_enabled $numStr")
    }

    private suspend fun applyUniversalAuto(enable: Boolean, touched: MutableList<String>) {
        // First check Transsion presence (Infinix, Tecno, Itel)
        val transsionCheck = RootCommander.execSilent("[ -f /sys/devices/platform/tran_battery/OTG_CTL ] && echo exists")
        if (transsionCheck.stdout.contains("exists")) {
            RootCommander.log(LogType.INFO, "Found Transsion OTG_CTL node! Triggering...")
            applyTranssionNodes(enable, touched)
        }

        // Check MediaTek otg_switch
        val mtkCheck = RootCommander.execSilent("[ -f /sys/class/power_supply/usb/otg_switch ] && echo exists")
        if (mtkCheck.stdout.contains("exists")) {
            RootCommander.log(LogType.INFO, "Found MediaTek power_supply/usb/otg_switch! Triggering...")
            applyMtkNodes(enable, touched)
        }

        // Check Dual-Role USB
        val dualRoleCheck = RootCommander.execSilent("ls /sys/class/dual_role_usb/ 2>/dev/null")
        if (dualRoleCheck.stdout.trim().isNotBlank()) {
            RootCommander.log(LogType.INFO, "Found Dual-Role USB subsystems! Triggering DFP & Source...")
            applyDualRoleNodes(enable, touched)
        }

        // Check USB Role
        val roleCheck = RootCommander.execSilent("ls /sys/class/usb_role/ 2>/dev/null")
        if (roleCheck.stdout.trim().isNotBlank()) {
            RootCommander.log(LogType.INFO, "Found modern USB Role switches! Triggering host role...")
            applyUsbRoleNodes(enable, touched)
        }

        // Check Type-C TCPM
        val typecCheck = RootCommander.execSilent("[ -d /sys/class/typec/port0 ] && echo exists")
        if (typecCheck.stdout.contains("exists")) {
            RootCommander.log(LogType.INFO, "Found Type-C Port0! Triggering host data role...")
            applyTypecNodes(enable, touched)
        }

        // Always apply OEM persist properties (safe fallback for OnePlus/Oppo/Realme/Xiaomi)
        applyOemProperties(enable)
        touched.add("System OEM Properties")

        // If no hardware node matched yet, run generic fallback across all sysfs nodes
        if (touched.size <= 1) {
            RootCommander.log(LogType.WARNING, "No specific primary hardware node matched; executing full hardware sweep...")
            applyQualcommNodes(enable, touched)
            applyExtconNodes(enable, touched)
        }
    }

    private suspend fun writeNodeIfExists(path: String, value: String, touched: MutableList<String>) {
        val check = RootCommander.execSilent("[ -e \"$path\" ] && echo exists")
        if (check.stdout.contains("exists")) {
            val cmd = "echo \"$value\" > \"$path\""
            val res = RootCommander.exec(cmd)
            if (res.isSuccess) {
                touched.add("$path = $value")
                RootCommander.log(LogType.SUCCESS, "Written: $path -> $value")
            } else {
                RootCommander.log(LogType.WARNING, "Failed to write $path: ${res.stderr}")
            }
        }
    }

    private suspend fun writeNodePattern(pattern: String, value: String, touched: MutableList<String>) {
        val cmd = "for f in $pattern; do [ -e \"\$f\" ] && echo \"$value\" > \"\$f\" && echo \"OK \$f\"; done"
        val res = RootCommander.execSilent(cmd)
        if (res.isSuccess && res.stdout.contains("OK")) {
            for (line in res.stdout.trim().lines()) {
                if (line.startsWith("OK ")) {
                    val p = line.substring(3).trim()
                    touched.add("$p = $value")
                    RootCommander.log(LogType.SUCCESS, "Written: $p -> $value")
                }
            }
        }
    }

    suspend fun writeCustomNode(path: String, value: String): Boolean = withContext(Dispatchers.IO) {
        val cmd = "echo \"$value\" > \"$path\""
        val res = RootCommander.exec(cmd)
        if (res.isSuccess) {
            RootCommander.log(LogType.SUCCESS, "Custom write OK: $path -> $value")
            true
        } else {
            RootCommander.log(LogType.ERROR, "Custom write failed: ${res.stderr}")
            false
        }
    }

    suspend fun scanSysfsNodes(): List<DiscoveredNode> = withContext(Dispatchers.IO) {
        _isScanning.value = true
        RootCommander.log(LogType.INFO, "Scanning system for USB OTG / role / VBUS sysfs nodes...")

        val cmd = "find /sys/class /sys/devices -maxdepth 5 -type f " +
                "\\( -name \"*otg*\" -o -name \"*dual_role*\" -o -name \"*usb_role*\" -o -name \"*vbus*\" -o -name \"data_role\" -o -name \"power_role\" \\) " +
                "2>/dev/null | grep -v '/uevent' | head -n 45"

        val res = RootCommander.execSilent(cmd)
        val list = mutableListOf<DiscoveredNode>()

        if (res.isSuccess && res.stdout.isNotBlank()) {
            for (line in res.stdout.trim().lines()) {
                val p = line.trim()
                if (p.isNotBlank()) {
                    val catRes = RootCommander.execSilent("cat \"$p\" 2>/dev/null")
                    val permRes = RootCommander.execSilent("ls -l \"$p\" 2>/dev/null")
                    val currentVal = if (catRes.isSuccess) catRes.stdout.trim().take(40) else "(unreadable)"
                    val perms = permRes.stdout.split(" ").firstOrNull() ?: "-rw-r--r--"
                    val writable = perms.contains("w")

                    val type = when {
                        p.contains("tran_battery") -> "Transsion Battery"
                        p.contains("dual_role") -> "Dual-Role Subsystem"
                        p.contains("usb_role") -> "USB Role Switch"
                        p.contains("typec") -> "Type-C Port Manager"
                        p.contains("otg_switch") -> "MTK OTG Switch"
                        p.contains("extcon") -> "Extcon State"
                        p.contains("dwc3") -> "Qualcomm/DWC3"
                        else -> "Sysfs Control"
                    }

                    list.add(
                        DiscoveredNode(
                            path = p,
                            currentValue = currentVal,
                            permissions = perms,
                            isWritable = writable,
                            candidateType = type
                        )
                    )
                }
            }
        }

        _discoveredNodes.value = list
        _isScanning.value = false
        RootCommander.log(LogType.SUCCESS, "Found ${list.size} candidate sysfs OTG nodes.")
        list
    }
}
