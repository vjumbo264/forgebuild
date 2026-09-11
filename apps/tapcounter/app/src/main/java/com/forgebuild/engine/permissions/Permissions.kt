package com.forgebuild.engine.permissions

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * ForgeBuild permission wiring. Every sensitive capability is OPT-IN:
 * the generating AI enables only what the app needs (see PermissionSet) and
 * adds the matching <uses-permission> entry to AndroidManifest.xml plus, for
 * device admin, a receiver wired to res/xml/device_admin.xml.
 *
 * Covered at minimum: storage, camera, notifications, foreground service,
 * background location, device administrator.
 */
enum class EnginePermission(val manifests: List<String>) {
    STORAGE(
        if (Build.VERSION.SDK_INT >= 33)
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    ),
    CAMERA(listOf(Manifest.permission.CAMERA)),
    NOTIFICATIONS(
        if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
    ),
    FOREGROUND_SERVICE(
        buildList {
            add(Manifest.permission.FOREGROUND_SERVICE)
            if (Build.VERSION.SDK_INT >= 34) add(Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE)
        }
    ),
    BACKGROUND_LOCATION(listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)),
    // BACKGROUND_LOCATION runtime grant for ACCESS_BACKGROUND_LOCATION is a
    // separate settings-screen flow on Android 11+; see requestBackground().
    DEVICE_ADMIN(emptyList()) // policy-driven, not a runtime permission
}

object PermissionWiring {
    fun isGranted(ctx: Context, p: String): Boolean =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun missing(ctx: Context, perm: EnginePermission): List<String> =
        perm.manifests.filter { !isGranted(ctx, it) }

    /** Request runtime permissions for a capability; no-op if already granted. */
    fun request(activity: Activity, perm: EnginePermission, requestCode: Int = perm.ordinal + 100) {
        val toAsk = missing(activity, perm)
        if (toAsk.isNotEmpty()) ActivityCompat.requestPermissions(activity, toAsk.toTypedArray(), requestCode)
    }

    /** Android 11+: background location must be granted from Settings. */
    fun requestBackgroundLocation(activity: Activity) {
        request(activity, EnginePermission.BACKGROUND_LOCATION)
        if (Build.VERSION.SDK_INT >= 30 &&
            !isGranted(activity, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        ) {
            activity.startActivity(
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(
                    android.net.Uri.fromParts("package", activity.packageName, null)
                )
            )
        }
    }

    /** Device admin: prompt to activate the app's DeviceAdminReceiver. */
    fun requestDeviceAdmin(activity: Activity, receiver: Class<*>, explanation: String) {
        val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val comp = ComponentName(activity, receiver)
        if (!dpm.isAdminActive(comp)) {
            activity.startActivity(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp)
                    .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, explanation)
            )
        }
    }
}
