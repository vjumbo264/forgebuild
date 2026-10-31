package com.forgebuild.otgenabler

import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DiagnosticsExporter {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US)

    suspend fun buildReport(
        controller: OtgController,
        usbMonitor: UsbMonitor
    ): String = withContext(Dispatchers.IO) {
        val unameRes = RootCommander.execSilent("uname -a 2>&1")
        val uname = unameRes.stdout.trim().ifBlank { unameRes.combinedError.ifBlank { "unavailable" } }
        val selinux = RootCommander.getSELinuxStatus()
        val rootStatus = RootCommander.rootStatus.value
        val rootFlavor = RootCommander.rootFlavor.value
        val rootId = RootCommander.rootIdOutput.value.ifBlank { "(not captured)" }

        val eval = controller.evaluation.value
        val strategy = controller.selectedStrategy.value
        val nodes = controller.discoveredNodes.value
        val writes = RootCommander.lastWriteResults.value
        val dmesgBefore = controller.lastDmesgBefore.value
        val dmesgAfter = controller.lastDmesgAfter.value
        val dmesgDiff = controller.lastDmesgDiff.value

        val liveDmesgNow = if (dmesgAfter.isBlank()) {
            controller.captureFilteredDmesg(tailLines = 80)
        } else {
            ""
        }

        val usbListing = usbMonitor.captureDiagnosticsListing()

        val propRes = RootCommander.execSilent(
            "getprop 2>/dev/null | grep -i -E 'usb|otg|typec|mtk|charge' | grep -vi 'serialno' | head -n 120"
        )
        val filteredProps = propRes.stdout.trim().ifBlank { "(none)" }

        buildString {
            appendLine("=== OTG ENABLER v2 DIAGNOSTICS REPORT ===")
            appendLine("Generated: ${isoFormat.format(Date())}")
            appendLine()

            appendLine("--- 1. DEVICE & ROOT ENVIRONMENT ---")
            appendLine("Manufacturer / Brand : ${Build.MANUFACTURER} / ${Build.BRAND}")
            appendLine("Model / Device       : ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Product / Hardware   : ${Build.PRODUCT} / ${Build.HARDWARE} (board=${Build.BOARD})")
            appendLine("Android Version      : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Build Display        : ${Build.DISPLAY}")
            appendLine("Kernel (uname -a)    : $uname")
            appendLine("Root Provider        : $rootFlavor (status=$rootStatus)")
            appendLine("Root Context (id)    : $rootId")
            appendLine("SELinux Mode         : $selinux")
            appendLine("SELinux Option       : tryWithPermissive=${controller.tryWithSelinuxPermissive.value}")
            appendLine("Extra Legacy Tweaks  : ${controller.extraLegacyTweaks.value}")
            appendLine()

            appendLine("--- 2. CURRENT OTG VERIFICATION STATE ---")
            appendLine("Verification State   : ${eval.state.name} (${eval.statusBadge})")
            appendLine("Selected Strategy    : ${strategy.title} (${strategy.id})")
            appendLine("Host Role Confirmed  : ${eval.hostRoleConfirmed}")
            appendLine("5V VBUS Confirmed    : ${eval.vbusConfirmed}")
            appendLine("Applied Nodes Count  : ${eval.appliedCount}")
            appendLine("Real USB Accessories : ${eval.enumeratedDevicesCount}")
            appendLine("Subtitle             : ${eval.subtitle}")
            appendLine("Detail               : ${eval.detailMessage}")
            appendLine()

            appendLine("--- 3. DISCOVERED SYSFS NODES (${nodes.size} total; dummy_udc excluded) ---")
            if (nodes.isEmpty()) {
                appendLine("(no nodes scanned yet)")
            } else {
                for (n in nodes) {
                    val rwTag = if (n.isControllable) "WRITABLE" else "INFO-ONLY"
                    appendLine("[$rwTag] [${n.candidateType}] perms=${n.permissions} path=${n.path}")
                    if (n.realPath != n.path) {
                        appendLine("    realPath=${n.realPath}")
                    }
                    appendLine("    value=\"${n.currentValue}\" (active=\"${n.activeValue}\")")
                }
            }
            appendLine()

            appendLine("--- 4. STRUCTURED WRITE RESULTS FROM LAST ATTEMPT (${writes.size} entries) ---")
            if (writes.isEmpty()) {
                appendLine("(no write attempts recorded in this session)")
            } else {
                for ((idx, w) in writes.withIndex()) {
                    appendLine("${idx + 1}. ${w.toStructuredLog()}")
                }
            }
            appendLine()

            appendLine("--- 5. KERNEL DMESG (filtered: tcpc|typec|usb|otg|vbus|mtu3|musb|xhci) ---")
            if (dmesgBefore.isBlank() && dmesgAfter.isBlank()) {
                appendLine("[Current Snapshot (no apply attempt run yet)]:")
                appendLine(liveDmesgNow.ifBlank { "(empty or dmesg restricted)" })
            } else {
                appendLine("[DIFF — New lines added during last attempt (${dmesgDiff.size} lines)]:")
                if (dmesgDiff.isEmpty()) {
                    appendLine("(0 new matching kernel log lines)")
                } else {
                    dmesgDiff.forEach { appendLine(it) }
                }
                appendLine()
                appendLine("[BEFORE Snapshot (tail 40)]:")
                val bTail = dmesgBefore.lines().filter { it.isNotBlank() }.takeLast(40)
                if (bTail.isEmpty()) appendLine("(empty)") else bTail.forEach { appendLine(it) }
                appendLine()
                appendLine("[AFTER Snapshot (tail 40)]:")
                val aTail = dmesgAfter.lines().filter { it.isNotBlank() }.takeLast(40)
                if (aTail.isEmpty()) appendLine("(empty)") else aTail.forEach { appendLine(it) }
            }
            appendLine()

            appendLine("--- 6. /sys/bus/usb/devices LISTING ---")
            appendLine(usbListing)
            appendLine()

            appendLine("--- 7. FILTERED SYSTEM PROPERTIES (usb|otg|typec|mtk|charge) ---")
            appendLine(filteredProps)
            appendLine("=== END OF DIAGNOSTICS REPORT ===")
        }
    }
}
