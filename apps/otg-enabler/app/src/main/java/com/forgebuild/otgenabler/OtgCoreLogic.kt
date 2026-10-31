package com.forgebuild.otgenabler

enum class TargetKind(val label: String) {
    SYSFS("sysfs"),
    PROP("prop"),
    SETTING("setting")
}

data class SafeWriteResult(
    val path: String,
    val kind: TargetKind = TargetKind.SYSFS,
    val requested: String,
    val exists: Boolean,
    val permissions: String,
    val writable: Boolean,
    val before: String,
    val after: String,
    val exitCode: Int,
    val ok: Boolean,
    val stderr: String,
    val timestampMs: Long = System.currentTimeMillis()
) {
    val activeBeforeValue: String
        get() = SysfsReadbackParser.extractActiveValue(before)

    val activeAfterValue: String
        get() = SysfsReadbackParser.extractActiveValue(after)

    val changed: Boolean
        get() = ok && before.trim() != after.trim()

    fun toStructuredLog(): String {
        val status = if (ok) "OK" else "FAIL"
        val cleanBefore = before.replace('\n', ' ').replace('\r', ' ').trim()
        val cleanAfter = after.replace('\n', ' ').replace('\r', ' ').trim()
        val cleanErr = stderr.replace('\n', ' ').replace('\r', ' ').trim()
        val errPart = if (cleanErr.isNotBlank()) " | err=\"$cleanErr\"" else ""
        return "[$status] $path (${kind.label}) req=\"$requested\" before=\"$cleanBefore\" " +
            "after=\"$cleanAfter\" perms=$permissions writable=$writable exit=$exitCode$errPart"
    }
}

enum class NodeCategory(val label: String) {
    ROLE_SWITCH("role switch"),
    TYPEC_DATA_ROLE("typec data role"),
    TYPEC_POWER_ROLE("typec power role"),
    VBUS_OTG_REGULATOR("vbus / otg regulator"),
    CHARGER_OTG("charger otg"),
    EXTCON("extcon"),
    INFO_ONLY("info only");

    companion object {
        fun fromNameOrDefault(name: String): NodeCategory =
            entries.firstOrNull { it.name == name || it.label.equals(name, ignoreCase = true) } ?: INFO_ONLY
    }
}

data class DiscoveredNode(
    val path: String,
    val currentValue: String,
    val permissions: String,
    val isWritable: Boolean,
    val category: NodeCategory,
    val detailHint: String = "",
    val realPath: String = path
) {
    val candidateType: String
        get() = if (detailHint.isNotBlank()) "${category.label} ($detailHint)" else category.label

    val activeValue: String
        get() = SysfsReadbackParser.extractActiveValue(currentValue)

    val isControllable: Boolean
        get() = isWritable && category != NodeCategory.INFO_ONLY
}

enum class OtgVerificationState {
    DISABLED,
    VERIFIED,
    PARTIAL,
    FAILED
}

data class ApplyEvaluation(
    val state: OtgVerificationState,
    val appliedCount: Int,
    val hostRoleConfirmed: Boolean,
    val vbusConfirmed: Boolean,
    val enumeratedDevicesCount: Int,
    val headline: String,
    val statusBadge: String,
    val subtitle: String,
    val detailMessage: String
)

data class ConnectedUsbDevice(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val manufacturerName: String,
    val productName: String,
    val deviceClass: String,
    val isMassStorage: Boolean
) {
    val vendorHex: String get() = "0x%04X".format(vendorId)
    val productHex: String get() = "0x%04X".format(productId)

    val displayTitle: String
        get() = when {
            productName.isNotBlank() -> productName
            manufacturerName.isNotBlank() -> "$manufacturerName Device"
            else -> "USB Peripheral ($vendorHex:$productHex)"
        }
}

object SysfsReadbackParser {
    private val bracketRegex = Regex("""\[([^\[\]\s]+)\]""")

    private val truthyValues = setOf("1", "0x1", "0x01", "true", "on", "enable", "enabled", "yes", "y", "active")
    private val falsyValues = setOf("0", "0x0", "0x00", "false", "off", "disable", "disabled", "no", "n", "inactive")
    private val hostValues = setOf("host", "dfp", "a_host", "otg_host")
    private val deviceValues = setOf("device", "peripheral", "ufp", "none", "b_idle", "b_peripheral")

    /**
     * Extracts the currently active value from a sysfs readback string.
     * For bracketed selections such as "[host] device" or "host [device]",
     * returns only the bracketed active token ("host" or "device").
     */
    fun extractActiveValue(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() ||
            trimmed == "(unreadable)" ||
            trimmed == "(missing)" ||
            trimmed == "(write-only)" ||
            trimmed == "(empty)"
        ) {
            return ""
        }
        val bracketMatch = bracketRegex.find(trimmed)
        if (bracketMatch != null) {
            return bracketMatch.groupValues[1].trim()
        }
        val lines = trimmed.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size == 1) {
            val single = lines[0]
            if (single.contains(":") && !single.startsWith("/")) {
                val rhs = single.substringAfterLast(":").trim()
                if (rhs.isNotEmpty()) return rhs
            }
            if (single.contains("=") && !single.startsWith("USB-HOST=") && !single.startsWith("USB=")) {
                val rhs = single.substringAfterLast("=").trim()
                if (rhs.isNotEmpty()) return rhs
            }
            return single
        }
        return trimmed
    }

    /**
     * Determines whether the post-write readback confirms the requested intent.
     * Handles bracketed kernel role selectors (e.g. "[host] device" vs "host [device]"),
     * multi-line extcon state ("USB-HOST=1"), and standard boolean/role tokens.
     */
    fun matchesIntent(requested: String, rawAfter: String, path: String = ""): Boolean {
        val req = requested.trim()
        val after = rawAfter.trim()
        if (req.isEmpty() || after.isEmpty()) return false
        if (after == "(unreadable)" || after == "(missing)" || after == "(write-only)" || after == "(empty)") {
            return false
        }
        if (after.startsWith("(unreadable:") ||
            after.startsWith("cat:") ||
            after.contains("Permission denied", ignoreCase = true) ||
            after.contains("No such file", ignoreCase = true) ||
            after.contains("Invalid argument", ignoreCase = true)
        ) {
            return false
        }

        // Extcon multi-line KEY=VALUE format (e.g. USB-HOST=1)
        if (req.contains("=")) {
            return after.lines().any { line ->
                val clean = line.trim()
                clean.equals(req, ignoreCase = true) || clean.endsWith(req, ignoreCase = true)
            }
        }

        // Bracketed selector format: ONLY the token inside [ ] is active!
        val bracketMatch = bracketRegex.find(after)
        if (bracketMatch != null) {
            val activeToken = bracketMatch.groupValues[1].trim()
            return tokensEquivalent(req, activeToken, path)
        }

        val extracted = extractActiveValue(after)
        if (extracted.isNotEmpty() && tokensEquivalent(req, extracted, path)) {
            return true
        }

        return tokensEquivalent(req, after, path)
    }

    fun tokensEquivalent(requested: String, actual: String, path: String = ""): Boolean {
        val r = requested.trim().lowercase()
        val a = actual.trim().lowercase()
        if (r.isEmpty() || a.isEmpty()) return false
        if (r == a) return true

        if (r in setOf("1", "true", "on", "enable", "enabled")) {
            return a in truthyValues
        }
        if (r in setOf("0", "false", "off", "disable", "disabled")) {
            return a in falsyValues
        }
        if (r in setOf("host", "dfp")) {
            return a in hostValues
        }
        if (r in setOf("device", "peripheral", "ufp", "none")) {
            return a in deviceValues
        }
        if (r == "dual" || r == "drp") {
            return a == "dual" || a == "drp"
        }
        if (r == "source") {
            return a == "source"
        }
        if (r == "sink") {
            return a == "sink"
        }
        return false
    }

    /**
     * Checks whether a sysfs ls -lL permission string has any write bit (e.g. -rw-r--r--, --w-------)
     * and passes root write check. Read-only attributes (-r--r--r--) have no kernel .store handler
     * and cannot be written even by uid=0.
     */
    fun isSysfsModeWritable(lsPermissions: String, rootTestWritable: Boolean = true): Boolean {
        val p = lsPermissions.trim()
        if (p.isEmpty() || p.startsWith("d")) return false
        val modeSlice = if (p.length >= 10) p.substring(1, 10) else p.drop(1)
        val hasWriteBit = modeSlice.contains('w')
        return hasWriteBit && rootTestWritable
    }

    /**
     * Excludes virtual placeholder controllers (dummy_udc) and non-OTG kernel metadata noise.
     */
    fun shouldExcludePath(path: String): Boolean {
        val p = path.trim().lowercase()
        if (p.isEmpty()) return true
        if (p.contains("dummy_udc")) return true

        val excludedSuffixes = listOf(
            "/uevent", "/modalias", "/subsystem", "/driver", "/of_node",
            "/waiting_for_supplier", "/avoid_reset_quirk",
            "/descriptors", "/bos", "/idvendor", "/idproduct", "/bcddevice",
            "/bdeviceclass", "/bdevicesubclass", "/bdeviceprotocol",
            "/bmaxpacketsize0", "/bnumconfigurations", "/bnuminterfaces",
            "/bmattributes", "/bmaxpower", "/urbnum", "/ltm_capable",
            "/removable", "/authorized", "/quirks", "/tx_lanes", "/rx_lanes",
            "/devnum", "/busnum", "/devpath", "/version", "/maxchild",
            "/type", "/usb_power_delivery_revision", "/usb_typec_revision",
            "/waiting_for_supplier"
        )
        if (excludedSuffixes.any { p.endsWith(it) }) return true

        val excludedFragments = listOf(
            "/power/runtime_", "/power/autosuspend", "/power/control",
            "/power/wakeup", "/power/async", "/statistics/", "/ep_00", "/ep_"
        )
        if (excludedFragments.any { p.contains(it) }) return true

        return false
    }

    /**
     * Categorizes a sysfs node into one of the 7 required categories (Section 6.2).
     * Any node that is not writable as root is strictly marked INFO_ONLY (read-only).
     */
    fun categorizeNode(
        path: String,
        isWritable: Boolean,
        regulatorName: String = ""
    ): Pair<NodeCategory, String> {
        val p = path.trim().lowercase()
        val fileName = p.substringAfterLast('/')

        val baseCategory = when {
            p.contains("/usb_role/") ||
                (fileName == "role" && !p.contains("typec")) ||
                ((p.contains("mt_usb") || p.contains("musb") || p.contains("mtu3") ||
                    p.contains("dwc3") || p.contains("msm_otg") || p.contains("/debug/usb")) &&
                    fileName in setOf("mode", "otg_mode", "host_mode")) -> NodeCategory.ROLE_SWITCH

            fileName == "data_role" ||
                (p.contains("/dual_role_usb/") && fileName == "mode") -> NodeCategory.TYPEC_DATA_ROLE

            fileName in setOf("power_role", "port_type", "preferred_role", "pr_swap") -> NodeCategory.TYPEC_POWER_ROLE

            p.contains("/regulator/") ||
                p.contains("otg_vbus") || p.contains("otg-vbus") || p.contains("usb_otg_vbus") ||
                p.contains("usb-otg-vbus") ||
                (fileName.contains("vbus") && !p.contains("/power_supply/")) -> NodeCategory.VBUS_OTG_REGULATOR

            p.contains("/extcon/") -> NodeCategory.EXTCON

            p.contains("/power_supply/") || p.contains("charger") || p.contains("tran_battery") ||
                p.contains("tcpc") || p.contains("rt-pd") ||
                fileName.contains("otg") || fileName.contains("boost") -> NodeCategory.CHARGER_OTG

            else -> NodeCategory.CHARGER_OTG
        }

        if (!isWritable) {
            val hint = buildString {
                append(baseCategory.label)
                if (regulatorName.isNotBlank()) {
                    append(": ")
                    append(regulatorName)
                }
                append(", read-only")
            }
            return NodeCategory.INFO_ONLY to hint
        }

        return baseCategory to regulatorName
    }

    /**
     * Parses tab-delimited scanner output lines:
     * NODE\t<path>\t<realPath>\t<perms>\t<tw>\t<regName>\t<value>
     * Deduplicates symlinked /sys/class vs /sys/devices paths (preferring /sys/class),
     * excludes dummy_udc, and sorts writable controllable nodes before info-only nodes.
     */
    fun parseScannerDump(dump: String): List<DiscoveredNode> {
        if (dump.isBlank()) return emptyList()
        val byCanonical = linkedMapOf<String, DiscoveredNode>()

        for (rawLine in dump.lines()) {
            val line = rawLine.trimEnd()
            if (!line.startsWith("NODE\t")) continue
            val parts = line.split('\t')
            if (parts.size < 6) continue

            val path = parts[1].trim()
            val realPath = parts[2].trim().ifBlank { path }
            val perms = parts[3].trim().ifBlank { "-r--r--r--" }
            val tw = parts[4].trim() == "1"
            val regName = parts[5].trim()
            val value = parts.getOrNull(6)?.trim().orEmpty().ifBlank { "(empty)" }

            if (shouldExcludePath(path) || shouldExcludePath(realPath)) continue

            val writable = isSysfsModeWritable(perms, rootTestWritable = tw)
            val (category, hint) = categorizeNode(path, writable, regName)

            val candidate = DiscoveredNode(
                path = path,
                currentValue = value.take(80),
                permissions = perms,
                isWritable = writable,
                category = category,
                detailHint = hint,
                realPath = realPath
            )

            val existing = byCanonical[realPath]
            if (existing == null) {
                byCanonical[realPath] = candidate
            } else {
                // Prefer /sys/class/... canonical alias over deep /sys/devices/... path
                val preferNew = !existing.path.startsWith("/sys/class/") && path.startsWith("/sys/class/")
                if (preferNew) {
                    byCanonical[realPath] = candidate
                }
            }
        }

        val categoryOrder = mapOf(
            NodeCategory.ROLE_SWITCH to 0,
            NodeCategory.TYPEC_DATA_ROLE to 1,
            NodeCategory.TYPEC_POWER_ROLE to 2,
            NodeCategory.CHARGER_OTG to 3,
            NodeCategory.VBUS_OTG_REGULATOR to 4,
            NodeCategory.EXTCON to 5,
            NodeCategory.INFO_ONLY to 6
        )

        return byCanonical.values.sortedWith(
            compareBy<DiscoveredNode>(
                { if (it.isControllable) 0 else 1 },
                { categoryOrder[it.category] ?: 99 },
                { it.path }
            )
        )
    }
}

object UsbSysfsParser {
    private val usbRootNameRegex = Regex("""^usb\d+$""", RegexOption.IGNORE_CASE)

    /**
     * Returns true if the /sys/bus/usb/devices entry is a Linux Foundation root hub (1d6b),
     * a usbN bus root entry with hub class 09, or a USB interface descriptor (contains ':').
     */
    fun isRootHubOrInterface(
        entryName: String,
        vendorId: Int,
        productId: Int,
        deviceClassHex: String = ""
    ): Boolean {
        val cleanName = entryName.trim()
        if (cleanName.isEmpty()) return true
        if (cleanName.contains(":")) return true
        if (vendorId == 0x1D6B) return true
        if (usbRootNameRegex.matches(cleanName)) return true
        val cls = deviceClassHex.trim().lowercase().removePrefix("0x")
        if (cls == "09" && (cleanName.startsWith("usb", ignoreCase = true) || cleanName.endsWith("-0"))) {
            return true
        }
        if (vendorId <= 0 && productId <= 0) return true
        return false
    }

    /**
     * Parses tab-delimited output from /sys/bus/usb/devices/* inspection:
     * ENTRY\t<name>\t<idVendor>\t<idProduct>\t<bDeviceClass>\t<manufacturer>\t<product>\t<speed>\t<ifClasses>
     */
    fun parseSysfsDevicesDump(dump: String): List<ConnectedUsbDevice> {
        if (dump.isBlank()) return emptyList()
        val results = mutableListOf<ConnectedUsbDevice>()
        for (rawLine in dump.lines()) {
            val line = rawLine.trim()
            if (!line.startsWith("ENTRY\t")) continue
            val parts = line.split('\t')
            if (parts.size < 5) continue

            val entryName = parts[1].trim()
            val vidStr = parts[2].trim().removePrefix("0x")
            val pidStr = parts[3].trim().removePrefix("0x")
            val bDeviceClass = parts[4].trim()
            val manufacturer = parts.getOrNull(5)?.trim().orEmpty()
            val product = parts.getOrNull(6)?.trim().orEmpty()
            val ifClassesRaw = parts.getOrNull(8)?.trim().orEmpty()

            val vid = vidStr.toIntOrNull(16) ?: continue
            val pid = pidStr.toIntOrNull(16) ?: continue

            if (isRootHubOrInterface(entryName, vid, pid, bDeviceClass)) {
                continue
            }

            val allClasses = buildList {
                if (bDeviceClass.isNotBlank()) add(bDeviceClass.lowercase().removePrefix("0x"))
                ifClassesRaw.split(',').map { it.trim().lowercase().removePrefix("0x") }
                    .filter { it.isNotBlank() }
                    .forEach { add(it) }
            }

            val isMassStorage = allClasses.any { it == "08" || it == "8" }
            val classDesc = classifyFromHexClasses(bDeviceClass, allClasses)

            results.add(
                ConnectedUsbDevice(
                    deviceName = "/sys/bus/usb/devices/$entryName",
                    vendorId = vid,
                    productId = pid,
                    manufacturerName = manufacturer,
                    productName = product,
                    deviceClass = classDesc,
                    isMassStorage = isMassStorage
                )
            )
        }
        return results
    }

    fun classifyFromHexClasses(bDeviceClass: String, allClasses: List<String>): String {
        val primary = bDeviceClass.trim().lowercase().removePrefix("0x")
        val candidates = if (primary.isNotBlank() && primary != "00" && primary != "0" && primary != "ef") {
            listOf(primary) + allClasses
        } else {
            allClasses
        }
        for (c in candidates) {
            when (c.padStart(2, '0')) {
                "08" -> return "Mass Storage (Flash Drive)"
                "03" -> return "HID (Keyboard / Mouse / Input)"
                "01" -> return "USB Audio (DAC / Headset)"
                "0e" -> return "USB Video (Camera / UVC)"
                "09" -> return "External USB Hub"
                "02", "0a" -> return "Communications / Serial / Ethernet"
                "07" -> return "USB Printer"
                "0b" -> return "Smart Card Reader"
                "e0" -> return "Wireless Controller"
            }
        }
        return "USB Accessory"
    }
}

object DmesgDiffHelper {
    fun computeNewLines(before: String, after: String, maxLines: Int = 80): List<String> {
        val beforeLines = before.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
        val afterLines = after.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
        if (afterLines.isEmpty()) return emptyList()
        if (beforeLines.isEmpty()) return afterLines.takeLast(maxLines)

        val lastBefore = beforeLines.last()
        val lastIdx = afterLines.lastIndexOf(lastBefore)
        if (lastIdx >= 0) {
            return afterLines.drop(lastIdx + 1).takeLast(maxLines)
        }

        val beforeSet = beforeLines.toHashSet()
        return afterLines.filter { it !in beforeSet }.takeLast(maxLines)
    }

    private val positiveVbusRegex = Regex(
        """(source_vbus\s*[:=]\s*(1|true|on|enable)|otg_boost\s*[:=]?\s*(1|on|enable)|vbus[_\s-]*(on|enabled|source\s*on)|set_vbus.*\b(1|on|enable)\b|enable_otg\s*[:=]?\s*1)""",
        RegexOption.IGNORE_CASE
    )
    private val negativeVbusRegex = Regex(
        """(fail|error|err\b|disable|off\b|[:=]\s*0\b|sink|cannot|denied|not\s+supported)""",
        RegexOption.IGNORE_CASE
    )

    fun hasConfirmedVbusInDmesg(diffLines: List<String>): Boolean {
        return diffLines.any { line ->
            positiveVbusRegex.containsMatchIn(line) && !negativeVbusRegex.containsMatchIn(line)
        }
    }
}

object OtgStateEvaluator {
    fun evaluate(
        strategyTitle: String,
        writeResults: List<SafeWriteResult>,
        hostRoleConfirmed: Boolean,
        vbusConfirmed: Boolean,
        vbusEvidence: String,
        hostEvidence: String,
        enumeratedDevices: List<ConnectedUsbDevice>
    ): ApplyEvaluation {
        val sysfsResults = writeResults.filter { it.kind == TargetKind.SYSFS }
        val okWrites = writeResults.filter { it.ok }
        val okSysfsWrites = sysfsResults.filter { it.ok }
        val failedWrites = writeResults.filter { !it.ok }
        val devCount = enumeratedDevices.size

        if (okSysfsWrites.isEmpty() && !hostRoleConfirmed && !vbusConfirmed) {
            val firstErrors = failedWrites.take(3).joinToString("; ") { r ->
                "${r.path.substringAfterLast('/')}: ${r.stderr.ifBlank { "rejected" }}"
            }
            val detail = if (writeResults.isEmpty() || okWrites.isEmpty()) {
                if (firstErrors.isNotBlank()) {
                    "0 nodes applied, nothing changed. Errors: $firstErrors"
                } else {
                    "0 nodes applied, nothing changed (no writable nodes matched strategy '$strategyTitle')."
                }
            } else {
                "0 hardware sysfs nodes applied ($firstErrors)."
            }
            return ApplyEvaluation(
                state = OtgVerificationState.FAILED,
                appliedCount = 0,
                hostRoleConfirmed = false,
                vbusConfirmed = false,
                enumeratedDevicesCount = devCount,
                headline = "0 Nodes Applied",
                statusBadge = "FAILED — NO CHANGES",
                subtitle = "0 nodes applied, nothing changed",
                detailMessage = detail
            )
        }

        if (hostRoleConfirmed && vbusConfirmed) {
            val sub = if (devCount > 0) {
                "Supplying 5V VBUS power ($devCount USB accessory attached)"
            } else {
                "Supplying 5V VBUS power (host & VBUS verified; waiting for USB accessory)"
            }
            val detail = buildString {
                append("Host confirmed ($hostEvidence); VBUS confirmed ($vbusEvidence).")
                if (devCount > 0) {
                    append(" Enumerated: ${enumeratedDevices.joinToString { it.displayTitle }}.")
                } else {
                    append(" No non-root-hub USB device enumerated yet.")
                }
            }
            return ApplyEvaluation(
                state = OtgVerificationState.VERIFIED,
                appliedCount = okSysfsWrites.size,
                hostRoleConfirmed = true,
                vbusConfirmed = true,
                enumeratedDevicesCount = devCount,
                headline = "USB OTG Verified",
                statusBadge = "VERIFIED HOST + 5V VBUS",
                subtitle = sub,
                detailMessage = detail
            )
        }

        if (hostRoleConfirmed && !vbusConfirmed) {
            val powerErrors = failedWrites.filter {
                it.path.contains("power_role") || it.path.contains("vbus") ||
                    it.path.contains("otg") || it.path.contains("regulator") || it.path.contains("charger")
            }.take(2).joinToString("; ") { "${it.path.substringAfterLast('/')}: ${it.stderr}" }

            val detail = buildString {
                append("Host role confirmed ($hostEvidence), but 5V VBUS power is NOT confirmed")
                if (devCount == 0) append(" and 0 USB accessories enumerated")
                append(".")
                if (powerErrors.isNotBlank()) {
                    append(" Unconfirmed/failed power step: $powerErrors")
                } else {
                    append(" Unconfirmed step: no writable charger/regulator VBUS boost switch confirmed 5V output.")
                }
            }
            return ApplyEvaluation(
                state = OtgVerificationState.PARTIAL,
                appliedCount = okSysfsWrites.size,
                hostRoleConfirmed = true,
                vbusConfirmed = false,
                enumeratedDevicesCount = devCount,
                headline = "Host Role Set (Partial)",
                statusBadge = "PARTIAL — VBUS UNCONFIRMED",
                subtitle = "Host role set, VBUS not confirmed",
                detailMessage = detail
            )
        }

        if (!hostRoleConfirmed && vbusConfirmed) {
            val roleErrors = failedWrites.filter {
                it.path.contains("role") || it.path.contains("mode")
            }.take(2).joinToString("; ") { "${it.path.substringAfterLast('/')}: ${it.stderr}" }
            val detail = "5V VBUS confirmed ($vbusEvidence), but USB host data role is unconfirmed" +
                if (roleErrors.isNotBlank()) " ($roleErrors)." else "."
            return ApplyEvaluation(
                state = OtgVerificationState.PARTIAL,
                appliedCount = okSysfsWrites.size,
                hostRoleConfirmed = false,
                vbusConfirmed = true,
                enumeratedDevicesCount = devCount,
                headline = "VBUS On (Role Unconfirmed)",
                statusBadge = "PARTIAL — ROLE UNCONFIRMED",
                subtitle = "VBUS active, host data role not confirmed",
                detailMessage = detail
            )
        }

        val failSummary = failedWrites.take(3).joinToString("; ") {
            "${it.path.substringAfterLast('/')}: ${it.stderr}"
        }.ifBlank { "Readback did not confirm host role or VBUS." }

        return ApplyEvaluation(
            state = OtgVerificationState.FAILED,
            appliedCount = okSysfsWrites.size,
            hostRoleConfirmed = false,
            vbusConfirmed = false,
            enumeratedDevicesCount = devCount,
            headline = "OTG Enable Failed",
            statusBadge = "FAILED",
            subtitle = "Host role and VBUS could not be verified",
            detailMessage = "Failed step(s): $failSummary"
        )
    }
}
