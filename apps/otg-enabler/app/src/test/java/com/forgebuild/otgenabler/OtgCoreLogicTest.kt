package com.forgebuild.otgenabler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OtgCoreLogicTest {

    @Test
    fun `bracketed readback parser matches only active bracketed token`() {
        // "[host] device" -> active is host
        assertEquals("host", SysfsReadbackParser.extractActiveValue("[host] device"))
        assertTrue(SysfsReadbackParser.matchesIntent("host", "[host] device"))
        assertFalse(SysfsReadbackParser.matchesIntent("device", "[host] device"))

        // "host [device]" -> active is device, NOT host!
        assertEquals("device", SysfsReadbackParser.extractActiveValue("host [device]"))
        assertFalse(SysfsReadbackParser.matchesIntent("host", "host [device]"))
        assertTrue(SysfsReadbackParser.matchesIntent("device", "host [device]"))

        // Type-C power_role "[sink] source" vs "sink [source]"
        assertFalse(SysfsReadbackParser.matchesIntent("source", "source [sink]"))
        assertTrue(SysfsReadbackParser.matchesIntent("source", "[source] sink"))
        assertEquals("dual", SysfsReadbackParser.extractActiveValue("[dual] source sink"))
        assertTrue(SysfsReadbackParser.matchesIntent("dual", "[dual] source sink"))
    }

    @Test
    fun `readback parser handles boolean, regulator, extcon, and unreadable values`() {
        assertTrue(SysfsReadbackParser.matchesIntent("1", "1"))
        assertTrue(SysfsReadbackParser.matchesIntent("1", "enabled"))
        assertTrue(SysfsReadbackParser.matchesIntent("enabled", "enabled"))
        assertFalse(SysfsReadbackParser.matchesIntent("1", "0"))
        assertFalse(SysfsReadbackParser.matchesIntent("1", "disabled"))

        val extconMultiLine = "USB=0\nUSB-HOST=1\nCHARGER=0"
        assertTrue(SysfsReadbackParser.matchesIntent("USB-HOST=1", extconMultiLine))
        assertFalse(SysfsReadbackParser.matchesIntent("USB-HOST=0", extconMultiLine))

        assertFalse(SysfsReadbackParser.matchesIntent("host", "(unreadable)"))
        assertFalse(SysfsReadbackParser.matchesIntent("host", "cat: /sys/foo: Permission denied"))
        assertFalse(SysfsReadbackParser.matchesIntent("host", ""))
    }

    @Test
    fun `scanner excludes dummy_udc and marks read-only nodes as info only`() {
        assertTrue(
            SysfsReadbackParser.shouldExcludePath(
                "/sys/devices/platform/dummy_udc.0/udc/dummy_udc.0/is_otg"
            )
        )
        assertFalse(SysfsReadbackParser.isSysfsModeWritable("-r--r--r--", rootTestWritable = false))
        assertFalse(SysfsReadbackParser.isSysfsModeWritable("-r--r--r--", rootTestWritable = true))
        assertTrue(SysfsReadbackParser.isSysfsModeWritable("-rw-r--r--", rootTestWritable = true))

        val rawDump = buildString {
            appendLine("NODE\t/sys/devices/platform/dummy_udc.0/udc/dummy_udc.0/is_otg\t/sys/devices/platform/dummy_udc.0/udc/dummy_udc.0/is_otg\t-r--r--r--\t0\t\t1")
            appendLine("NODE\t/sys/devices/platform/11200000.usb/usb_role/mt_usb-role-switch/role\t/sys/devices/platform/11200000.usb/usb_role/mt_usb-role-switch/role\t-rw-r--r--\t1\t\tdevice")
            appendLine("NODE\t/sys/class/usb_role/mt_usb-role-switch/role\t/sys/devices/platform/11200000.usb/usb_role/mt_usb-role-switch/role\t-rw-r--r--\t1\t\tdevice")
            appendLine("NODE\t/sys/class/extcon/extcon0/state\t/sys/devices/platform/extcon0/extcon/extcon0/state\t-r--r--r--\t0\textcon0\tUSB=0")
            appendLine("NODE\t/sys/class/regulator/regulator.14/state\t/sys/devices/platform/reg14/state\t-r--r--r--\t0\tusb-otg-vbus\tdisabled")
        }

        val parsed = SysfsReadbackParser.parseScannerDump(rawDump)
        // dummy_udc excluded; symlink deduplicated in favor of /sys/class/...
        assertEquals(3, parsed.size)
        assertFalse(parsed.any { it.path.contains("dummy_udc") })

        val roleNode = parsed.first { it.path.contains("mt_usb-role-switch") }
        assertEquals("/sys/class/usb_role/mt_usb-role-switch/role", roleNode.path)
        assertEquals(NodeCategory.ROLE_SWITCH, roleNode.category)
        assertTrue(roleNode.isControllable)

        val extconNode = parsed.first { it.path.contains("extcon0") }
        assertEquals(NodeCategory.INFO_ONLY, extconNode.category)
        assertFalse(extconNode.isControllable)

        val regNode = parsed.first { it.path.contains("regulator.14") }
        assertEquals(NodeCategory.INFO_ONLY, regNode.category)
        assertTrue(regNode.candidateType.contains("usb-otg-vbus"))
    }

    @Test
    fun `usb sysfs parser ignores 1d6b root hubs and usbN bus entries while keeping real accessories`() {
        // Exact scenario from mt6768 with nothing plugged in: two Linux Foundation 2.0 root hubs
        val emptyBusDump = buildString {
            appendLine("ENTRY\tusb1\t1d6b\t0002\t09\tLinux 6.6\txHCI Host Controller\t480\t09")
            appendLine("ENTRY\tusb2\t1d6b\t0002\t09\tLinux 6.6\tMUSB HDRC host driver\t480\t09")
            appendLine("ENTRY\t1-0:1.0\t1d6b\t0002\t09\t\t\t480\t09")
        }
        val emptyResult = UsbSysfsParser.parseSysfsDevicesDump(emptyBusDump)
        assertTrue("Expected 0 real USB devices when only 1d6b root hubs exist", emptyResult.isEmpty())

        // Plug in a real Kingston DataTraveler flash drive (0951:1666) + external VIA hub (2109:2817)
        val populatedDump = emptyBusDump + buildString {
            appendLine("ENTRY\t1-1\t2109\t2817\t09\tVIA Labs\tUSB2.0 Hub\t480\t09")
            appendLine("ENTRY\t1-1.2\t0951\t1666\t00\tKingston\tDataTraveler 3.0\t480\t08")
        }
        val realResult = UsbSysfsParser.parseSysfsDevicesDump(populatedDump)
        assertEquals(2, realResult.size)
        assertEquals(0x2109, realResult[0].vendorId)
        assertEquals("External USB Hub", realResult[0].deviceClass)
        assertEquals(0x0951, realResult[1].vendorId)
        assertTrue(realResult[1].isMassStorage)
        assertEquals("Mass Storage (Flash Drive)", realResult[1].deviceClass)
    }

    @Test
    fun `state evaluator reports Failed when 0 nodes applied, Partial when VBUS unconfirmed, and Verified when both confirmed`() {
        // 1. Transsion strategy applying 0 nodes -> FAILED ("0 nodes applied, nothing changed")
        val zeroEval = OtgStateEvaluator.evaluate(
            strategyTitle = "Transsion / Tecno / Infinix",
            writeResults = emptyList(),
            hostRoleConfirmed = false,
            vbusConfirmed = false,
            vbusEvidence = "",
            hostEvidence = "",
            enumeratedDevices = emptyList()
        )
        assertEquals(OtgVerificationState.FAILED, zeroEval.state)
        assertEquals("0 nodes applied, nothing changed", zeroEval.subtitle)

        // 2. mt6768 partial scenario: role=host succeeded, port_type=dual succeeded, power_role=source failed
        val writesPartial = listOf(
            SafeWriteResult(
                path = "/sys/class/usb_role/mt_usb-role-switch/role",
                requested = "host",
                exists = true,
                permissions = "-rw-r--r--",
                writable = true,
                before = "device",
                after = "host",
                exitCode = 0,
                ok = true,
                stderr = ""
            ),
            SafeWriteResult(
                path = "/sys/class/typec/port0/power_role",
                requested = "source",
                exists = true,
                permissions = "-rw-r--r--",
                writable = true,
                before = "source [sink]",
                after = "source [sink]",
                exitCode = 1,
                ok = false,
                stderr = "tee: /sys/class/typec/port0/power_role: Operation not supported (exit=1)"
            )
        )
        val partialEval = OtgStateEvaluator.evaluate(
            strategyTitle = "Universal Smart Auto-Detect",
            writeResults = writesPartial,
            hostRoleConfirmed = true,
            vbusConfirmed = false,
            vbusEvidence = "",
            hostEvidence = "role==host",
            enumeratedDevices = emptyList()
        )
        assertEquals(OtgVerificationState.PARTIAL, partialEval.state)
        assertEquals("Host role set, VBUS not confirmed", partialEval.subtitle)
        assertFalse(partialEval.subtitle.contains("Supplying 5V VBUS"))
        assertTrue(partialEval.detailMessage.contains("Operation not supported"))

        // 3. Full verified scenario: host role + VBUS confirmed
        val writesVerified = writesPartial.take(1) + SafeWriteResult(
            path = "/sys/class/power_supply/usb/otg_switch",
            requested = "1",
            exists = true,
            permissions = "-rw-rw-r--",
            writable = true,
            before = "0",
            after = "1",
            exitCode = 0,
            ok = true,
            stderr = ""
        )
        val verifiedEval = OtgStateEvaluator.evaluate(
            strategyTitle = "MediaTek + TCPC / Charger VBUS",
            writeResults = writesVerified,
            hostRoleConfirmed = true,
            vbusConfirmed = true,
            vbusEvidence = "otg_switch==1",
            hostEvidence = "role==host",
            enumeratedDevices = emptyList()
        )
        assertEquals(OtgVerificationState.VERIFIED, verifiedEval.state)
        assertTrue(verifiedEval.subtitle.contains("Supplying 5V VBUS"))
    }

    @Test
    fun `dmesg diff helper computes new lines and identifies genuine VBUS source events`() {
        val before = "[  100.100] mt_usb: idle\n[  101.200] tcpc_typec: sink attached"
        val after = before + "\n[  105.300] mt_usb: role switch to host\n[  105.450] tcpc: source_vbus: 1"
        val diff = DmesgDiffHelper.computeNewLines(before, after)
        assertEquals(2, diff.size)
        assertTrue(DmesgDiffHelper.hasConfirmedVbusInDmesg(diff))

        val errorDiff = listOf("[  106.000] tcpc: source_vbus: 0 failed not supported")
        assertFalse(DmesgDiffHelper.hasConfirmedVbusInDmesg(errorDiff))
    }
}
