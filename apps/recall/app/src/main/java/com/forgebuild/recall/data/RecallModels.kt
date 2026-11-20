package com.forgebuild.recall.data

import java.util.UUID

/**
 * Built-in distinctive synthesized chimes (44.1 kHz PCM16, USAGE_ALARM).
 * Each sound has a distinct timbre/rhythm so users can recognize intervals
 * without looking at their device.
 */
enum class RecallSound(
    val displayName: String,
    val description: String,
) {
    CRYSTAL_BELL(
        displayName = "Crystal Bell",
        description = "Crisp single high harmonic bell strike"
    ),
    DOUBLE_STRIKE(
        displayName = "Double Strike",
        description = "Two clean mid-register bell strikes"
    ),
    CHIME_TRIAD(
        displayName = "Chime Triad",
        description = "Ascending three-note resonant chord chime"
    ),
    SONAR_PING(
        displayName = "Sonar Ping",
        description = "Focused low-mid resonant awareness ping"
    ),
    METRONOME_CLICK(
        displayName = "Woodblock Tap",
        description = "Sharp two-tone percussive woodblock cue"
    ),
    SOFT_PULSE(
        displayName = "Warm Pulse",
        description = "Rounded marimba-like double tone"
    ),
    WESTMINSTER_CUE(
        displayName = "Four-Note Cue",
        description = "Classic four-note quarter-hour clock motif"
    );

    companion object {
        fun fromName(name: String?): RecallSound =
            entries.firstOrNull { it.name == name } ?: CRYSTAL_BELL
    }
}

enum class RecallStatus {
    STOPPED,
    ACTIVE,
    PAUSED;

    companion object {
        fun fromName(name: String?): RecallStatus =
            entries.firstOrNull { it.name == name } ?: STOPPED
    }
}

/**
 * A clock-aligned recurring time-awareness interval.
 *
 * [priority] determines which chime plays when multiple intervals coincide on
 * the same clock boundary (higher number = higher priority).
 */
data class RecallInterval(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val minutes: Int,
    val enabled: Boolean = true,
    val sound: RecallSound = RecallSound.CRYSTAL_BELL,
    val volume: Float = 0.8f,
    val priority: Int = PRIORITY_CUSTOM,
) {
    companion object {
        const val PRIORITY_LOW = 25
        const val PRIORITY_CUSTOM = 50
        const val PRIORITY_MEDIUM_HIGH = 75
        const val PRIORITY_HIGHEST = 100
    }
}

data class ScheduledChime(
    val boundaryEpochMs: Long,
    val winningInterval: RecallInterval,
    val coincidingIntervals: List<RecallInterval>,
)

data class RecallAppState(
    val status: RecallStatus = RecallStatus.STOPPED,
    val intervals: List<RecallInterval> = RecallDefaults.defaultIntervals(),
    val headsUpNotificationsEnabled: Boolean = true,
    val highPrecisionAlarmClockMode: Boolean = false,
    val lastFiredBoundaryMs: Long = 0L,
    val onboardingDone: Boolean = false,
)

object RecallDefaults {
    fun defaultIntervals(): List<RecallInterval> = listOf(
        RecallInterval(
            id = "default_5m",
            label = "Every 5 minutes",
            minutes = 5,
            enabled = true,
            sound = RecallSound.CRYSTAL_BELL,
            volume = 0.70f,
            priority = RecallInterval.PRIORITY_LOW,
        ),
        RecallInterval(
            id = "default_30m",
            label = "Every 30 minutes",
            minutes = 30,
            enabled = true,
            sound = RecallSound.DOUBLE_STRIKE,
            volume = 0.85f,
            priority = RecallInterval.PRIORITY_MEDIUM_HIGH,
        ),
        RecallInterval(
            id = "default_60m",
            label = "Hourly chime",
            minutes = 60,
            enabled = true,
            sound = RecallSound.CHIME_TRIAD,
            volume = 1.00f,
            priority = RecallInterval.PRIORITY_HIGHEST,
        ),
    )

    fun defaultPriorityForMinutes(minutes: Int): Int = when {
        minutes >= 60 && minutes % 60 == 0 -> RecallInterval.PRIORITY_HIGHEST
        minutes == 30 -> RecallInterval.PRIORITY_MEDIUM_HIGH
        minutes == 5 -> RecallInterval.PRIORITY_LOW
        else -> RecallInterval.PRIORITY_CUSTOM
    }

    fun defaultLabelForMinutes(minutes: Int): String = when (minutes) {
        1 -> "Every minute"
        60 -> "Hourly chime"
        else -> "Every $minutes minutes"
    }
}
