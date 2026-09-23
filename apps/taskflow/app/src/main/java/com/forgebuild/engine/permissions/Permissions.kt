package com.forgebuild.engine.permissions

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

enum class EnginePermission(val manifests: List<String>) {
    CAMERA(listOf(Manifest.permission.CAMERA)),
    RECORD_AUDIO(listOf(Manifest.permission.RECORD_AUDIO)),
    MICROPHONE(listOf(Manifest.permission.RECORD_AUDIO)),
    POST_NOTIFICATIONS(
        if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
    ),
    LOCATION(listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)),
    BACKGROUND_LOCATION(
        if (Build.VERSION.SDK_INT >= 29) listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION) else emptyList()
    ),
    CONTACTS(listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)),
    CALENDAR(listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)),
    PHONE(listOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG)),
    SMS(listOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS)),
    BODY_SENSORS(listOf(Manifest.permission.BODY_SENSORS)),
    ACTIVITY_RECOGNITION(
        if (Build.VERSION.SDK_INT >= 29) listOf(Manifest.permission.ACTIVITY_RECOGNITION) else emptyList()
    ),
    BLUETOOTH(
        if (Build.VERSION.SDK_INT >= 31)
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
        else listOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
    ),
    NFC(listOf(Manifest.permission.NFC)),
    BIOMETRIC(emptyList()),
    EXACT_ALARM(
        if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.USE_EXACT_ALARM)
        else if (Build.VERSION.SDK_INT >= 31) listOf(Manifest.permission.SCHEDULE_EXACT_ALARM)
        else emptyList()
    ),
    ALL_FILES_ACCESS(emptyList()),
    INSTALL_UNKNOWN_APPS(listOf(Manifest.permission.REQUEST_INSTALL_PACKAGES)),
    OVERLAY(listOf(Manifest.permission.SYSTEM_ALERT_WINDOW)),
    ACCESSIBILITY_SERVICE(emptyList()),
    NOTIFICATION_LISTENER(emptyList()),
    VPN_SERVICE(emptyList()),
    DEVICE_ADMIN(emptyList())
}

object PermissionWiring {
    fun isGranted(ctx: Context, p: String): Boolean =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun missing(ctx: Context, perm: EnginePermission): List<String> =
        perm.manifests.filter { !isGranted(ctx, it) }

    fun request(activity: Activity, perm: EnginePermission, requestCode: Int = perm.ordinal + 100) {
        val toAsk = missing(activity, perm)
        if (toAsk.isNotEmpty()) ActivityCompat.requestPermissions(activity, toAsk.toTypedArray(), requestCode)
    }

    fun isBatteryOptimizationExempt(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Triggers the real OS-level battery optimization exemption prompt. */
    fun requestBatteryExemption(context: Context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (pm != null && !pm.isIgnoringBatteryOptimizations(context.packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                // Fallback to settings screen if direct prompt fails on certain OEMs
                try {
                    val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallback)
                } catch (_: Exception) {}
            }
        }
    }

    /** True when the app may schedule exact alarms (API 31+); true below 31. */
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        return am.canScheduleExactAlarms()
    }

    fun requestExactAlarm(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 31) {
            val am = activity.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!am.canScheduleExactAlarms()) {
                activity.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        .setData(Uri.fromParts("package", activity.packageName, null))
                )
            }
        }
    }
}
