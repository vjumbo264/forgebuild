package com.forgebuild.recall.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.forgebuild.recall.MainActivity
import com.forgebuild.recall.R
import com.forgebuild.recall.data.RecallAppState
import com.forgebuild.recall.data.RecallInterval
import com.forgebuild.recall.data.RecallStatus
import com.forgebuild.recall.data.ScheduledChime
import com.forgebuild.recall.receiver.RecallActionReceiver
import java.util.Date

/**
 * Notification channels & builders for RECALL.
 *
 * Adapted from TaskFlow's NotificationHub:
 * - [CHANNEL_FOREGROUND] is IMPORTANCE_LOW, silent, no badge.
 * - [CHANNEL_CHIME] is IMPORTANCE_HIGH for optional visual heads-up alerts,
 *   explicitly silenced (`setSound(null, null)`) so it never collides with
 *   [com.forgebuild.recall.audio.RecallAudioEngine]'s USAGE_ALARM audio.
 */
object RecallNotificationHub {
    const val CHANNEL_FOREGROUND = "recall_service"
    const val CHANNEL_CHIME = "recall_chime_alerts"

    const val FOREGROUND_NOTIFICATION_ID = 1001
    const val CHIME_NOTIFICATION_ID = 2002

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val fgChannel = NotificationChannel(
            CHANNEL_FOREGROUND,
            "RECALL Time Awareness",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Persistent status showing when RECALL is active or paused and the next scheduled chime"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        val chimeChannel = NotificationChannel(
            CHANNEL_CHIME,
            "Chime Visual Alerts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Optional heads-up visual notice accompanying scheduled audio chimes"
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannels(listOf(fgChannel, chimeChannel))
    }

    fun formatClock(context: Context, epochMs: Long): String =
        DateFormat.getTimeFormat(context).format(Date(epochMs))

    fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun launchPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionPendingIntent(context: Context, action: String, reqCode: Int): PendingIntent {
        val intent = Intent(context, RecallActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun buildForegroundNotification(
        context: Context,
        state: RecallAppState,
        nextChime: ScheduledChime?,
    ): Notification {
        ensureChannels(context)
        val enabledCount = state.intervals.count { it.enabled }

        val (title, body) = when (state.status) {
            RecallStatus.ACTIVE -> {
                if (nextChime != null) {
                    val timeText = formatClock(context, nextChime.boundaryEpochMs)
                    "RECALL Active · Next at $timeText" to
                        "${nextChime.winningInterval.label} (${nextChime.winningInterval.sound.displayName}) · $enabledCount enabled"
                } else {
                    "RECALL Active · No enabled intervals" to
                        "Enable at least one interval in RECALL to hear clock chimes."
                }
            }

            RecallStatus.PAUSED -> {
                "RECALL Paused" to
                    "Time awareness is paused. Tap Resume to sync with the next clock boundary."
            }

            RecallStatus.STOPPED -> {
                "RECALL" to "Stopped"
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_FOREGROUND)
            .setSmallIcon(R.drawable.ic_stat_recall)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(launchPendingIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        when (state.status) {
            RecallStatus.ACTIVE -> {
                builder.addAction(
                    0,
                    "Pause",
                    actionPendingIntent(context, RecallActionReceiver.ACTION_PAUSE, 101),
                )
                builder.addAction(
                    0,
                    "Stop",
                    actionPendingIntent(context, RecallActionReceiver.ACTION_STOP, 102),
                )
            }

            RecallStatus.PAUSED -> {
                builder.addAction(
                    0,
                    "Resume",
                    actionPendingIntent(context, RecallActionReceiver.ACTION_RESUME, 103),
                )
                builder.addAction(
                    0,
                    "Stop",
                    actionPendingIntent(context, RecallActionReceiver.ACTION_STOP, 102),
                )
            }

            RecallStatus.STOPPED -> Unit
        }
        return builder.build()
    }

    fun updateForegroundNotification(
        context: Context,
        state: RecallAppState,
        nextChime: ScheduledChime?,
    ) {
        if (!canPostNotifications(context)) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching {
            nm.notify(FOREGROUND_NOTIFICATION_ID, buildForegroundNotification(context, state, nextChime))
        }
    }

    fun postHeadsUpChime(
        context: Context,
        boundaryMs: Long,
        interval: RecallInterval,
    ) {
        if (!canPostNotifications(context)) return
        ensureChannels(context)
        val timeText = formatClock(context, boundaryMs)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(context, CHANNEL_CHIME)
            .setSmallIcon(R.drawable.ic_stat_recall)
            .setContentTitle("RECALL · $timeText")
            .setContentText("${interval.label} · ${interval.sound.displayName}")
            .setContentIntent(launchPendingIntent(context))
            .setAutoCancel(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { nm.notify(CHIME_NOTIFICATION_ID, notif) }
    }

    fun cancelChimeAlert(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { nm.cancel(CHIME_NOTIFICATION_ID) }
    }
}
