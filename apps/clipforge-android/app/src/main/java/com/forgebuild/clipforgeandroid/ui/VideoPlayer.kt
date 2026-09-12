package com.forgebuild.clipforgeandroid.ui

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * In-app playback for a downloaded final MP4 (operator fix #5) — the operator never
 * has to leave the app to check a render. Plays the MediaStore/content Uri (or legacy
 * file path) recorded in the downloads registry at download time.
 */
@Composable
fun VideoPlayerDialog(uriString: String, title: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 1) },
        text = {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        val controller = MediaController(ctx)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        if (uriString.startsWith("content://") || uriString.startsWith("file://")) {
                            setVideoURI(Uri.parse(uriString))
                        } else {
                            setVideoPath(uriString)
                        }
                        setOnPreparedListener { start() }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
