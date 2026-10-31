package com.forgebuild.otgenabler

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Enumerates attached USB accessories from `/sys/bus/usb/devices/<entry>` and `UsbManager`,
 * strictly ignoring Linux Foundation root hubs (`1d6b`), `usbN` root hub bus entries,
 * and `:` interface subdirectories (Section 6.5).
 */
class UsbMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as? UsbManager

    private val _connectedDevices = MutableStateFlow<List<ConnectedUsbDevice>>(emptyList())
    val connectedDevices: StateFlow<List<ConnectedUsbDevice>> = _connectedDevices.asStateFlow()

    private val _lastRawSysfsDump = MutableStateFlow("")
    val lastRawSysfsDump: StateFlow<String> = _lastRawSysfsDump.asStateFlow()

    suspend fun refresh(): List<ConnectedUsbDevice> = withContext(Dispatchers.IO) {
        val rawDump = readSysfsUsbDump()
        _lastRawSysfsDump.value = rawDump

        val merged = linkedMapOf<String, ConnectedUsbDevice>()

        // 1. Primary authoritative source: /sys/bus/usb/devices/* (filtered via UsbSysfsParser)
        for (dev in UsbSysfsParser.parseSysfsDevicesDump(rawDump)) {
            val key = "${dev.deviceName}|${dev.vendorId}|${dev.productId}"
            merged[key] = dev
        }

        // 2. Reconcile with Android UsbManager (strictly applying the same root-hub filter)
        runCatching {
            usbManager?.deviceList?.values?.forEach { device ->
                val rawName = device.deviceName.orEmpty().substringAfterLast('/')
                val clsHex = "%02x".format(device.deviceClass)
                if (!UsbSysfsParser.isRootHubOrInterface(rawName, device.vendorId, device.productId, clsHex)) {
                    val alreadyMatched = merged.values.any {
                        it.vendorId == device.vendorId && it.productId == device.productId
                    }
                    if (!alreadyMatched) {
                        val key = "${device.deviceName}|${device.vendorId}|${device.productId}"
                        merged[key] = ConnectedUsbDevice(
                            deviceName = device.deviceName ?: "USB Accessory",
                            vendorId = device.vendorId,
                            productId = device.productId,
                            manufacturerName = device.manufacturerName.orEmpty().trim(),
                            productName = device.productName.orEmpty().trim(),
                            deviceClass = classifyUsbDevice(device),
                            isMassStorage = isMassStorageDevice(device)
                        )
                    }
                }
            }
        }

        val finalList = merged.values.toList()
        _connectedDevices.value = finalList
        finalList
    }

    suspend fun captureDiagnosticsListing(): String = withContext(Dispatchers.IO) {
        val dump = readSysfsUsbDump()
        _lastRawSysfsDump.value = dump
        if (dump.isBlank()) {
            return@withContext "(no entries in /sys/bus/usb/devices)"
        }
        buildString {
            for (rawLine in dump.lines()) {
                val line = rawLine.trim()
                if (!line.startsWith("ENTRY\t")) continue
                val p = line.split('\t')
                if (p.size < 5) continue
                val name = p[1]
                val vidHex = p[2]
                val pidHex = p[3]
                val cls = p[4]
                val mfg = p.getOrNull(5).orEmpty()
                val prd = p.getOrNull(6).orEmpty()
                val spd = p.getOrNull(7).orEmpty()
                val vid = vidHex.removePrefix("0x").toIntOrNull(16) ?: 0
                val pid = pidHex.removePrefix("0x").toIntOrNull(16) ?: 0
                val ignored = UsbSysfsParser.isRootHubOrInterface(name, vid, pid, cls)
                val tag = if (ignored) "[IGNORED root-hub/interface]" else "[REAL ACCESSORY]"
                appendLine("$tag $name  VID=$vidHex PID=$pidHex class=$cls speed=${spd}Mbps mfg=\"$mfg\" product=\"$prd\"")
            }
        }.trim().ifBlank { "(no USB device nodes found)" }
    }

    private suspend fun readSysfsUsbDump(): String {
        val rootRes = RootCommander.execSilent(SYSFS_USB_SCRIPT)
        if (rootRes.isSuccess && rootRes.stdout.isNotBlank()) {
            return rootRes.stdout
        }
        return readSysfsUsbViaJavaFallback()
    }

    private fun readSysfsUsbViaJavaFallback(): String {
        val base = File("/sys/bus/usb/devices")
        val children = base.listFiles() ?: return ""
        return buildString {
            for (dir in children.sortedBy { it.name }) {
                val vidFile = File(dir, "idVendor")
                val pidFile = File(dir, "idProduct")
                if (!vidFile.exists() || !pidFile.exists()) continue
                val vid = runCatching { vidFile.readText().trim() }.getOrDefault("")
                val pid = runCatching { pidFile.readText().trim() }.getOrDefault("")
                if (vid.isEmpty() || pid.isEmpty()) continue
                val cls = runCatching { File(dir, "bDeviceClass").readText().trim() }.getOrDefault("")
                val mfg = runCatching { File(dir, "manufacturer").readText().trim() }.getOrDefault("")
                val prd = runCatching { File(dir, "product").readText().trim() }.getOrDefault("")
                val spd = runCatching { File(dir, "speed").readText().trim() }.getOrDefault("")
                val ifClasses = runCatching {
                    dir.listFiles()
                        ?.filter { it.isDirectory && File(it, "bInterfaceClass").exists() }
                        ?.mapNotNull { sub -> runCatching { File(sub, "bInterfaceClass").readText().trim() }.getOrNull() }
                        ?.joinToString(",")
                        .orEmpty()
                }.getOrDefault("")
                append("ENTRY\t")
                append(dir.name).append('\t')
                append(vid).append('\t')
                append(pid).append('\t')
                append(cls).append('\t')
                append(mfg.replace('\t', ' ')).append('\t')
                append(prd.replace('\t', ' ')).append('\t')
                append(spd).append('\t')
                append(ifClasses).append('\n')
            }
        }
    }

    private fun isMassStorageDevice(device: UsbDevice): Boolean {
        if (device.deviceClass == UsbConstants.USB_CLASS_MASS_STORAGE) return true
        for (i in 0 until device.interfaceCount) {
            if (device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE) {
                return true
            }
        }
        return false
    }

    private fun classifyUsbDevice(device: UsbDevice): String {
        val ifClasses = (0 until device.interfaceCount).map { "%02x".format(device.getInterface(it).interfaceClass) }
        return UsbSysfsParser.classifyFromHexClasses("%02x".format(device.deviceClass), ifClasses)
    }

    companion object {
        private val SYSFS_USB_SCRIPT = """
            for d in /sys/bus/usb/devices/*; do
              [ -f "${'$'}d/idVendor" ] || continue
              [ -f "${'$'}d/idProduct" ] || continue
              bn=${'$'}(basename "${'$'}d")
              vid=${'$'}(cat "${'$'}d/idVendor" 2>/dev/null | tr -d '\r\n\t')
              pid=${'$'}(cat "${'$'}d/idProduct" 2>/dev/null | tr -d '\r\n\t')
              cls=${'$'}(cat "${'$'}d/bDeviceClass" 2>/dev/null | tr -d '\r\n\t')
              mfg=${'$'}(cat "${'$'}d/manufacturer" 2>/dev/null | tr '\r\n\t' '   ' | sed 's/^ *//;s/ *${'$'}//')
              prd=${'$'}(cat "${'$'}d/product" 2>/dev/null | tr '\r\n\t' '   ' | sed 's/^ *//;s/ *${'$'}//')
              spd=${'$'}(cat "${'$'}d/speed" 2>/dev/null | tr -d '\r\n\t')
              ifcls=""
              for sub in "${'$'}d"/*:*; do
                [ -f "${'$'}sub/bInterfaceClass" ] || continue
                ic=${'$'}(cat "${'$'}sub/bInterfaceClass" 2>/dev/null | tr -d '\r\n\t')
                if [ -n "${'$'}ic" ]; then
                  if [ -z "${'$'}ifcls" ]; then ifcls="${'$'}ic"; else ifcls="${'$'}ifcls,${'$'}ic"; fi
                fi
              done
              printf 'ENTRY\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "${'$'}bn" "${'$'}vid" "${'$'}pid" "${'$'}cls" "${'$'}mfg" "${'$'}prd" "${'$'}spd" "${'$'}ifcls"
            done
        """.trimIndent()
    }
}
