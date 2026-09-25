package com.forgebuild.everbrowse

import android.content.Context
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.PathParser
import com.forgebuild.engine.ui.components.EngineLinearWavyProgress
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.SpacingTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PauseIcon: ImageVector by lazy {
    ImageVector.Builder(name = "Pause", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(PathParser().parsePathString("M6,19h4V5H6v14zm8,-14v14h4V5h-4z").toNodes(), fill = SolidColor(Color.Black))
        .build()
}

private val PlayIcon: ImageVector by lazy {
    ImageVector.Builder(name = "Play", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(PathParser().parsePathString("M8,5v14l11,-7z").toNodes(), fill = SolidColor(Color.Black))
        .build()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadManagerSheet(
    onDismiss: () -> Unit,
    onRetryDownload: ((DownloadRecord) -> Unit)? = null
) {
    val context = LocalContext.current
    val s = SpacingTokens.Spacing
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val downloads = DownloadCoordinator.downloads

    val activeDownloads = downloads.filter {
        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING || it.status == DownloadStatus.PAUSED
    }
    val finishedDownloads = downloads.filter {
        it.status != DownloadStatus.DOWNLOADING && it.status != DownloadStatus.PENDING && it.status != DownloadStatus.PAUSED
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
            // --- Header Row ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = s.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = EngineIcons.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(s.xs))
                Text(
                    text = "Downloads",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (downloads.isNotEmpty()) {
                    Spacer(Modifier.width(s.xxs))
                    Badge(
                        containerColor = if (activeDownloads.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = if (activeDownloads.isNotEmpty()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Text(
                            text = "${downloads.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.weight(1f))

                // Open system downloads folder button
                IconButton(
                    onClick = { DownloadCoordinator.openDownloadsFolder(context) }
                ) {
                    Icon(
                        imageVector = EngineIcons.Folder,
                        contentDescription = "Open Downloads Folder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Clear history button
                if (finishedDownloads.isNotEmpty()) {
                    IconButton(
                        onClick = { DownloadCoordinator.clearCompleted(context) }
                    ) {
                        Icon(
                            imageVector = EngineIcons.Delete,
                            contentDescription = "Clear History",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = EngineIcons.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = s.xs)
            )

            // --- Body Content ---
            if (downloads.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp, horizontal = s.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = EngineIcons.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(s.sm))
                    Text(
                        text = "No downloads yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(s.xxs))
                    Text(
                        text = "Files you download will appear here and in your device's Downloads folder.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = s.sm)
                    )
                    Spacer(Modifier.height(s.md))
                    FilledTonalButton(
                        onClick = { DownloadCoordinator.openDownloadsFolder(context) },
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(
                            imageVector = EngineIcons.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(s.xs))
                        Text("Open Downloads Folder", style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(s.xs)
                ) {
                    // --- Active Downloads Section ---
                    if (activeDownloads.isNotEmpty()) {
                        item {
                            Text(
                                text = "ACTIVE DOWNLOADS (${activeDownloads.size})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = s.xxs)
                            )
                        }
                        items(activeDownloads, key = { it.id }) { record ->
                            ActiveDownloadCard(
                                record = record,
                                onPause = { DownloadCoordinator.pauseDownload(context, record.id) },
                                onResume = { DownloadCoordinator.resumeDownload(context, record.id) },
                                onCancel = { DownloadCoordinator.cancelDownload(context, record.id) }
                            )
                        }
                    }

                    // --- Finished Downloads Section ---
                    if (finishedDownloads.isNotEmpty()) {
                        item {
                            Text(
                                text = "RECENT DOWNLOADS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = s.xs, bottom = s.xxs)
                            )
                        }
                        items(finishedDownloads, key = { it.id }) { record ->
                            FinishedDownloadCard(
                                record = record,
                                onOpen = { DownloadCoordinator.openFile(context, record) },
                                onShare = { DownloadCoordinator.shareFile(context, record) },
                                onDelete = { DownloadCoordinator.removeDownload(context, record, deleteFile = true) },
                                onRetry = { onRetryDownload?.invoke(record) ?: DownloadCoordinator.retryDownload(context, record) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveDownloadCard(
    record: DownloadRecord,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    val s = SpacingTokens.Spacing
    val isPaused = record.status == DownloadStatus.PAUSED
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isPaused) MaterialTheme.colorScheme.surfaceContainerHighest
            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(s.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (isPaused) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPaused) PauseIcon else EngineIcons.Download,
                            contentDescription = null,
                            tint = if (isPaused) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(Modifier.width(s.xs))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val statusText = if (isPaused) {
                        if (record.totalBytes > 0) {
                            val pct = ((record.downloadedBytes * 100) / record.totalBytes).toInt()
                            "Paused • $pct% (${DownloadCoordinator.formatBytes(record.downloadedBytes)} of ${DownloadCoordinator.formatBytes(record.totalBytes)})"
                        } else {
                            "Paused • ${DownloadCoordinator.formatBytes(record.downloadedBytes)}"
                        }
                    } else if (record.totalBytes > 0) {
                        val pct = ((record.downloadedBytes * 100) / record.totalBytes).toInt()
                        "$pct% • ${DownloadCoordinator.formatBytes(record.downloadedBytes)} of ${DownloadCoordinator.formatBytes(record.totalBytes)}"
                    } else {
                        "Downloading • ${DownloadCoordinator.formatBytes(record.downloadedBytes)}"
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Pause or Resume action
                if (isPaused) {
                    IconButton(
                        onClick = onResume,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = PlayIcon,
                            contentDescription = "Resume download",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onPause,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = PauseIcon,
                            contentDescription = "Pause download",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(Modifier.width(s.xxs))

                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = EngineIcons.Close,
                        contentDescription = "Cancel download",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.height(s.xs))
            EngineLinearWavyProgress(
                progress = { record.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            )
        }
    }
}

@Composable
private fun FinishedDownloadCard(
    record: DownloadRecord,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit
) {
    val s = SpacingTokens.Spacing
    val isCompleted = record.status == DownloadStatus.COMPLETED
    val isFailed = record.status == DownloadStatus.FAILED || record.status == DownloadStatus.CANCELLED

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isFailed) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isCompleted) { onOpen() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(s.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = when {
                    isCompleted -> MaterialTheme.colorScheme.surfaceContainerHighest
                    isFailed -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceContainer
                },
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = resolveFileIcon(record.fileName, record.mimeType, isCompleted),
                        contentDescription = null,
                        tint = when {
                            isCompleted -> MaterialTheme.colorScheme.primary
                            isFailed -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.width(s.xs))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val detailText = when {
                    isCompleted -> {
                        val sizeStr = DownloadCoordinator.formatBytes(record.downloadedBytes.takeIf { it > 0 } ?: record.totalBytes)
                        val dateStr = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(record.timestamp))
                        "$sizeStr • $dateStr"
                    }
                    isFailed -> record.errorMessage ?: "Download failed"
                    else -> "Pending"
                }
                Text(
                    text = detailText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isCompleted) {
                IconButton(
                    onClick = onShare,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = EngineIcons.Language,
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = EngineIcons.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else if (isFailed) {
                IconButton(
                    onClick = onRetry,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = EngineIcons.Refresh,
                        contentDescription = "Retry",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = EngineIcons.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private fun resolveFileIcon(fileName: String, mimeType: String, isCompleted: Boolean): ImageVector {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when {
        !isCompleted -> EngineIcons.Close
        ext in listOf("pdf", "doc", "docx", "txt", "rtf") -> EngineIcons.Newspaper
        ext in listOf("zip", "rar", "7z", "tar", "gz") -> EngineIcons.Folder
        ext in listOf("jpg", "jpeg", "png", "gif", "webp", "svg") -> EngineIcons.Language
        ext in listOf("mp4", "mkv", "webm", "avi", "mov") -> EngineIcons.DesktopWindows
        ext in listOf("mp3", "wav", "ogg", "m4a", "flac") -> EngineIcons.Bolt
        ext in listOf("kt", "java", "py", "js", "ts", "html", "css", "json", "xml") -> EngineIcons.Code
        else -> EngineIcons.Download
    }
}
