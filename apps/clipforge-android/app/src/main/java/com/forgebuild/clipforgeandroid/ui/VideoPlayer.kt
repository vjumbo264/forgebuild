package com.forgebuild.clipforgeandroid.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Proper native in-app player for downloaded final MP4s (session-10 fix #1).
 *
 * Replaces the old bare VideoView + default system MediaController (unstyled, no
 * fullscreen). This is a real Media3/ExoPlayer experience: play/pause, a seek bar
 * with live scrubbing, current/total time readouts, and a fullscreen toggle —
 * Media3's standard controller tinted to the app's Material 3 scheme
 * (ForgeBuildTheme) instead of the stock look. Fullscreen hides the system bars
 * and keeps the screen on; exiting restores both.
 */
@Composable
fun VideoPlayerDialog(uriString: String, title: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    var fullscreen by remember { mutableStateOf(false) }

    val player = remember(uriString) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uriString))
            prepare()
            playWhenReady = true
        }
    }

    // Keep the screen on while this player is open; hide system bars in fullscreen.
    DisposableEffect(fullscreen) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (fullscreen) {
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Release the player exactly once when the dialog leaves composition.
    DisposableEffect(Unit) { onDispose { player.release() } }

    if (fullscreen) {
        // True fullscreen surface — fills the whole display, no dialog chrome.
        Dialog(
            onDismissRequest = { fullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                PlayerSurface(player, Modifier.fillMaxSize())
                FullscreenGlyphButton(expand = false, modifier = Modifier.align(Alignment.TopEnd)) {
                    fullscreen = false
                }
            }
        }
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onDismiss) { Text("Close") }
                    }
                    Box(Modifier.fillMaxWidth().aspectRatio(9f / 16f)) {
                        PlayerSurface(player, Modifier.fillMaxSize())
                        FullscreenGlyphButton(expand = true, modifier = Modifier.align(Alignment.TopEnd)) {
                            fullscreen = true
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerSurface(player: ExoPlayer, modifier: Modifier = Modifier) {
    val controllerBg = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = true // standard controls: play/pause, seek bar + scrubbing, times
                controllerShowTimeoutMs = 4000
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                setBackgroundColor(android.graphics.Color.BLACK)
                // M3-themed controller bar instead of the stock unstyled look.
                setControllerBackgroundColor(controllerBg.toArgbInt())
                this.player = player
            }
        },
        update = { it.player = player },
        modifier = modifier.background(Color.Black)
    )
}

/** Fullscreen toggle — four corner brackets (expand) or inward corners (collapse). */
@Composable
private fun FullscreenGlyphButton(expand: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint = MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
        modifier = modifier.padding(8.dp)
    ) {
        Canvas(Modifier.padding(8.dp).size(20.dp)) {
            val stroke = 2.dp.toPx()
            val len = size.minDimension * 0.30f
            val w = size.width
            val h = size.height
            if (expand) {
                drawLine(tint, Offset(0f, len), Offset(0f, 0f), stroke)
                drawLine(tint, Offset(0f, 0f), Offset(len, 0f), stroke)
                drawLine(tint, Offset(w - len, 0f), Offset(w, 0f), stroke)
                drawLine(tint, Offset(w, 0f), Offset(w, len), stroke)
                drawLine(tint, Offset(0f, h - len), Offset(0f, h), stroke)
                drawLine(tint, Offset(0f, h), Offset(len, h), stroke)
                drawLine(tint, Offset(w - len, h), Offset(w, h), stroke)
                drawLine(tint, Offset(w, h), Offset(w, h - len), stroke)
            } else {
                drawLine(tint, Offset(len, 0f), Offset(len, len), stroke)
                drawLine(tint, Offset(len, len), Offset(0f, len), stroke)
                drawLine(tint, Offset(w - len, 0f), Offset(w - len, len), stroke)
                drawLine(tint, Offset(w - len, len), Offset(w, len), stroke)
                drawLine(tint, Offset(0f, h - len), Offset(len, h - len), stroke)
                drawLine(tint, Offset(len, h - len), Offset(len, h), stroke)
                drawLine(tint, Offset(w, h - len), Offset(w - len, h - len), stroke)
                drawLine(tint, Offset(w - len, h - len), Offset(w - len, h), stroke)
            }
        }
    }
}

/** Compose Color -> packed ARGB Int for the View-based controller background. */
private fun Color.toArgbInt(): Int =
    ((alpha * 255f + 0.5f).toInt() shl 24) or
        (((red * 255f + 0.5f).toInt()) shl 16) or
        (((green * 255f + 0.5f).toInt()) shl 8) or
        ((blue * 255f + 0.5f).toInt())
