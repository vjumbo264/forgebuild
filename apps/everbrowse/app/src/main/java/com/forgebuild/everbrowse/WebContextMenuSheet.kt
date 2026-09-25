package com.forgebuild.everbrowse

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.SpacingTokens

/**
 * Data payload describing the long-pressed web element (link, image, or image with link).
 */
data class WebContextMenuTarget(
    val type: Int,
    val linkUrl: String? = null,
    val imageUrl: String? = null,
    val titleOrText: String? = null
) {
    val isImage: Boolean
        get() = type == WebView.HitTestResult.IMAGE_TYPE || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE

    val isLink: Boolean
        get() = type == WebView.HitTestResult.SRC_ANCHOR_TYPE || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE

    val displayTitle: String
        get() = when {
            !titleOrText.isNullOrBlank() -> titleOrText
            !linkUrl.isNullOrBlank() -> linkUrl
            !imageUrl.isNullOrBlank() -> imageUrl
            else -> "Web Item"
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebContextMenuSheet(
    target: WebContextMenuTarget,
    onDismiss: () -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onOpenInBackgroundTab: (String) -> Unit,
    onDownloadImage: (String) -> Unit
) {
    val context = LocalContext.current
    val s = SpacingTokens.Spacing
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun copyToClipboard(label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
        onDismiss()
    }

    fun shareText(text: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(Intent.createChooser(intent, title))
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = s.xs),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Box(modifier = Modifier.size(width = 36.dp, height = 4.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = s.sm, vertical = s.xxs)
        ) {
            // Header: Preview of what was long-pressed
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = s.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when {
                                target.isImage && target.isLink -> EngineIcons.DesktopWindows
                                target.isImage -> EngineIcons.DesktopWindows
                                else -> EngineIcons.Language
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(s.xs))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (target.isImage && target.isLink) "Image Link"
                               else if (target.isImage) "Image"
                               else "Link",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = target.displayTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = s.xs)
            )

            // --- Link Options ---
            if (target.isLink && !target.linkUrl.isNullOrBlank()) {
                val url = target.linkUrl

                ContextMenuItem(
                    icon = EngineIcons.Add,
                    title = "Open in New Tab",
                    subtitle = "Open this link in a new active tab",
                    onClick = {
                        onOpenInNewTab(url)
                        onDismiss()
                    }
                )

                ContextMenuItem(
                    icon = EngineIcons.Tabs,
                    title = "Open in Background Tab",
                    subtitle = "Open link in a new tab without leaving current page",
                    onClick = {
                        onOpenInBackgroundTab(url)
                        onDismiss()
                    }
                )

                ContextMenuItem(
                    icon = EngineIcons.Code,
                    title = "Copy Link Address",
                    subtitle = url,
                    onClick = { copyToClipboard("Link address", url) }
                )

                ContextMenuItem(
                    icon = EngineIcons.Forum,
                    title = "Share Link",
                    subtitle = "Share URL with other apps",
                    onClick = { shareText(url, "Share link") }
                )
            }

            // Divider between Link options and Image options if both exist
            if (target.isLink && target.isImage) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = s.xs)
                )
            }

            // --- Image Options ---
            if (target.isImage && !target.imageUrl.isNullOrBlank()) {
                val imgUrl = target.imageUrl

                ContextMenuItem(
                    icon = EngineIcons.Download,
                    title = "Download Image",
                    subtitle = "Save image directly to Downloads",
                    onClick = {
                        onDownloadImage(imgUrl)
                        onDismiss()
                    }
                )

                ContextMenuItem(
                    icon = EngineIcons.DesktopWindows,
                    title = "Open Image in New Tab",
                    subtitle = "View full image in a new tab",
                    onClick = {
                        onOpenInNewTab(imgUrl)
                        onDismiss()
                    }
                )

                ContextMenuItem(
                    icon = EngineIcons.Code,
                    title = "Copy Image Link",
                    subtitle = imgUrl,
                    onClick = { copyToClipboard("Image link", imgUrl) }
                )

                ContextMenuItem(
                    icon = EngineIcons.Forum,
                    title = "Share Image Link",
                    subtitle = "Share image link with other apps",
                    onClick = { shareText(imgUrl, "Share image link") }
                )
            }

            Spacer(Modifier.height(s.sm))
        }
    }
}

@Composable
private fun ContextMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    val s = SpacingTokens.Spacing
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = s.sm, vertical = s.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.width(s.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
