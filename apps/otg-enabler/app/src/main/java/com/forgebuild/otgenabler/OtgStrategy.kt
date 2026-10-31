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
        description = "Ordered verified probe: USB role switch -> Type-C / TCPC -> Charger & Regulator 5V VBUS boost",
        chipVendor = "All SoCs & GSIs"
    ),
    MTK_TCPC_VBUS(
        id = "mtk_tcpc",
        title = "MediaTek + TCPC / Charger VBUS",
        description = "mt_usb / musb / mtu3 host role + rt-pd-manager / TCPC + MTK charger & usb-otg-vbus regulator",
        chipVendor = "MediaTek (mt6768 / Helio / Dimensity)"
    ),
    USB_ROLE_TYPEC(
        id = "usb_role_typec",
        title = "USB Role Switch + Type-C PD",
        description = "/sys/class/usb_role/*/role + /sys/class/typec/port* data_role & power_role + VBUS check",
        chipVendor = "Generic GKI / Type-C"
    ),
    TRANSSION_BATTERY(
        id = "transsion",
        title = "Transsion / Tecno / Infinix",
        description = "tran_battery OTG_CTL / otg_ctl boost switch + MediaTek USB host role switch",
        chipVendor = "Infinix / Tecno / Itel"
    ),
    DUAL_ROLE_DFP(
        id = "dual_role",
        title = "Linux Dual-Role USB (DFP)",
        description = "/sys/class/dual_role_usb/* dfp + host + source role switches & VBUS verification",
        chipVendor = "Dual-Role Subsystems"
    ),
    QUALCOMM_DWC3(
        id = "qualcomm",
        title = "Qualcomm / DWC3 Controller",
        description = "DWC3 / msm_otg / debugfs host mode registers + Type-C & VBUS regulator controls",
        chipVendor = "Qualcomm Snapdragon"
    ),
    EXTCON_CABLE(
        id = "extcon",
        title = "Extcon Cable ID Override",
        description = "Writes USB-HOST=1 only to verified writable /sys/class/extcon/*/state nodes",
        chipVendor = "Writable Extcon Drivers"
    ),
    AGGRESSIVE_ALL(
        id = "aggressive",
        title = "Aggressive All-In-One Hardware Sweep",
        description = "Tries every writable scanned OTG/role/TCPC/charger/regulator node and hardware fallback",
        chipVendor = "Maximum Hardware Coverage"
    );

    companion object {
        fun fromId(id: String): OtgStrategy = entries.firstOrNull { it.id == id } ?: UNIVERSAL_AUTO
    }
}
