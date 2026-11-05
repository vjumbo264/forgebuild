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
    val isGranted: Boolean
)

object PermissionsHelper {

    fun getPermissionList(context: Context): List<PermissionItem> {
        val list = mutableListOf<PermissionItem>()

        // 1. Save to Downloads
        val writeStorageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            true // MediaStore scoped storage requires no runtime permission
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        list.add(
            PermissionItem(
                id = "save_downloads",
                permission = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) Manifest.permission.WRITE_EXTERNAL_STORAGE else null,
                name = "Save to Downloads",
                purpose = "Save exported Keyflaw .kfpack and .cube LUTs to Downloads/MOTIONSALT/CC Studio/",
                whenRequested = "First export",
                fallback = "SAF Storage Access Framework folder picker",
                isGranted = writeStorageGranted
            )
        )

        // 2. Custom Save Folder
        list.add(
            PermissionItem(
                id = "saf_tree",
                permission = null, // Handled via ACTION_OPEN_DOCUMENT_TREE
                name = "Custom Save Folder",
                purpose = "User-picked custom export folder via Storage Access Framework",
                whenRequested = "Tapping Change folder in Export",
                fallback = "Default Downloads/MOTIONSALT/CC Studio/ folder",
                isGranted = true
            )
        )

        // 3. Direct Folder Access (MANAGE_EXTERNAL_STORAGE)
        val manageStorageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
        list.add(
            PermissionItem(
                id = "manage_storage",
                permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Manifest.permission.MANAGE_EXTERNAL_STORAGE else null,
                name = "Direct Folder Access",
                purpose = "Directly overwrite existing exports and merge packs in Downloads without extra prompts",
                whenRequested = "When enabled in Settings > Direct folder access",
                fallback = "Single file SAF picker",
                isGranted = manageStorageGranted
            )
        )

        // 4. Pick Frame Images
        val imageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        list.add(
            PermissionItem(
                id = "pick_images",
                permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE,
                name = "Pick Frames (Images)",
                purpose = "Select Base and Target frames for color correction matching",
                whenRequested = "Adding a pair from Gallery",
                fallback = "System Photo Picker or Files picker",
                isGranted = imageGranted
            )
        )

        // 5. Pick Video Frame
        val videoGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        list.add(
            PermissionItem(
                id = "video_frame",
                permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE,
                name = "Video Frame Extraction",
                purpose = "Pick a video clip and scrub to grab a reference or target frame",
                whenRequested = "Choosing Video frame source in Add pair",
                fallback = "System Files picker",
                isGranted = videoGranted
            )
        )

        // 6. Camera Frame
        val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        list.add(
            PermissionItem(
                id = "camera",
                permission = Manifest.permission.CAMERA,
                name = "Camera Frame",
                purpose = "Snap a live reference or color chart directly with the phone camera",
                whenRequested = "Choosing Camera source in Add pair",
                fallback = "Gallery photo picker or Files picker",
                isGranted = cameraGranted
            )
        )

        // 7. Background Notifications
        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        list.add(
            PermissionItem(
                id = "notifications",
                permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null,
                name = "Solver Notifications",
                purpose = "Foreground service notification showing progress during multi-step look fitting",
                whenRequested = "First time running Fit look",
                fallback = "Solver runs in foreground screen with in-app progress",
                isGranted = notifGranted
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
