package com.forgebuild.otgenabler

enum class OtgStrategy(
    val id: String,
    val title: String,
    val description: String,
    val chipVendor: String
) {
    UNIVERSAL_AUTO(
        id = "auto",
        title = "Universal Smart Auto-Detect",
        description = "Probes all known sysfs nodes across all vendors and executes optimal host triggers",
        chipVendor = "All Chipsets"
    ),
    TRANSSION_BATTERY(
        id = "transsion",
        title = "Transsion / Tecno / Infinix",
        description = "Original upstream method: /sys/devices/platform/tran_battery/OTG_CTL (MTK/Transsion)",
        chipVendor = "MediaTek / Transsion"
    ),
    MTK_POWER_SUPPLY(
        id = "mtk",
        title = "MediaTek OTG Switch",
        description = "/sys/class/power_supply/usb/otg_switch and /sys/devices/platform/mt_usb/otg_mode",
        chipVendor = "MediaTek"
    ),
    DUAL_ROLE_DFP(
        id = "dual_role",
        title = "Linux Dual-Role Framework",
        description = "/sys/class/dual_role_usb/*/mode (forces DFP host and source power roles)",
        chipVendor = "Standard Linux Dual-Role"
    ),
    USB_ROLE_HOST(
        id = "usb_role",
        title = "Modern USB Role Switch",
        description = "/sys/class/usb_role/*/role (mainline kernel 5.x/6.x USB role controller)",
        chipVendor = "Kernel 5.4+ / AOSP"
    ),
    TYPEC_TCPM(
        id = "typec",
        title = "Type-C Port Manager (TCPM)",
        description = "/sys/class/typec/port0/data_role and power_role (USB-PD / Type-C)",
        chipVendor = "Type-C USB-PD"
    ),
    QUALCOMM_DWC3(
        id = "qualcomm",
        title = "Qualcomm / DWC3 Controller",
        description = "/sys/devices/platform/soc/*.dwc3/otg_mode and ci_hdrc host role registers",
        chipVendor = "Qualcomm Snapdragon"
    ),
    EXTCON_CABLE(
        id = "extcon",
        title = "Extcon Cable ID Emulator",
        description = "/sys/class/extcon/*/state (simulates grounded OTG ID pin in hardware)",
        chipVendor = "Qualcomm / MTK / Samsung"
    ),
    OEM_PROPERTIES(
        id = "oem_props",
        title = "OEM System Properties",
        description = "OnePlus/Oppo/Realme persist.sys.oem.otg_support & global otg_state",
        chipVendor = "BBK / OnePlus / Oppo"
    ),
    AGGRESSIVE_ALL(
        id = "aggressive",
        title = "Aggressive All-In-One Force",
        description = "Applies every single known sysfs node, role switch, and property trigger",
        chipVendor = "Maximum Compatibility"
    );

    companion object {
        fun fromId(id: String): OtgStrategy = entries.firstOrNull { it.id == id } ?: UNIVERSAL_AUTO
    }
}
