package com.motionsalt.ccstudio.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

data class PermissionItem(
    val id: String,
    val permission: String?,
    val name: String,
    val purpose: String,
    val whenRequested: String,
    val fallback: String,
    val isGranted: Boolean,
    val statusText: String
)

object PermissionsHelper {

    fun getPermissionList(context: Context): List<PermissionItem> {
        val list = mutableListOf<PermissionItem>()

        // 1. Photos & Videos
        val mediaGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        val mediaPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        list.add(
            PermissionItem(
                id = "media",
                permission = mediaPerm,
                name = "Media (Photos & Videos)",
                purpose = "Allow quick batch access to reference frames in device albums",
                whenRequested = "When tapping Gallery or Video frame in Add pair",
                fallback = "System Photo Picker works without granting runtime permission",
                isGranted = mediaGranted,
                statusText = if (mediaGranted) "Granted" else "Not granted (Optional)"
            )
        )

        // 2. Camera
        val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        list.add(
            PermissionItem(
                id = "camera",
                permission = Manifest.permission.CAMERA,
                name = "Camera",
                purpose = "Snap a live reference frame or color chart directly with phone camera",
                whenRequested = "When tapping Camera in Add pair",
                fallback = "Pick saved photos from Gallery or Files",
                isGranted = cameraGranted,
                statusText = if (cameraGranted) "Granted" else "Not granted"
            )
        )

        // 3. Look Fitting Notifications (API 33+)
        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val notifStatus = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> "Not needed on this Android version"
            notifGranted -> "Granted"
            else -> "Not granted"
        }
        list.add(
            PermissionItem(
                id = "notifications",
                permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null,
                name = "Solver Progress Notifications",
                purpose = "Foreground notification showing background fitting progress",
                whenRequested = "First time running Fit look",
                fallback = "Solver runs with in-app live progress bar",
                isGranted = notifGranted,
                statusText = notifStatus
            )
        )

        // 4. Direct Folder Access (MANAGE_EXTERNAL_STORAGE)
        val manageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
        val manageStatus = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> "Not needed on this Android version"
            manageGranted -> "Granted"
            else -> "Not granted (Optional)"
        }
        list.add(
            PermissionItem(
                id = "manage_storage",
                permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Manifest.permission.MANAGE_EXTERNAL_STORAGE else null,
                name = "Direct Folder Access",
                purpose = "Directly overwrite and merge .kfpack packs in Downloads without extra confirmation prompts",
                whenRequested = "When enabled in Settings > Direct folder access",
                fallback = "Standard per-file SAF document picker",
                isGranted = manageGranted,
                statusText = manageStatus
            )
        )

        return list
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openManageStorageSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        }
    }
}
