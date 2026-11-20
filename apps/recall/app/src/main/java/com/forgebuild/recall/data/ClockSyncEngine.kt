package com.forgebuild.recall.data

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Global wall-clock synchronization and priority resolution engine.
 *
 * Every interval aligns strictly to real-world local clock boundaries
 * (e.g., 5m -> :00, :05, :10...; 15m -> :00, :15, :30, :45; 60m -> :00),
 * never relative to when the user tapped Start.
 */
object ClockSyncEngine {

    /** Alarm firing within this window before target boundary still counts as on-time. */
    const val EARLY_TOLERANCE_MS = 2_000L

    /** Alarms delivered later than this after target boundary are suppressed (no missed-chime backlog). */
    const val MISSED_GRACE_MS = 90_000L

    sealed class TriggerDecision {
        data object NotActive : TriggerDecision()
        data class TooEarly(val rescheduleFromMs: Long) : TriggerDecision()
        data class Duplicate(val rescheduleFromMs: Long) : TriggerDecision()
        data class MissedSuppressed(val lateByMs: Long, val rescheduleFromMs: Long) : TriggerDecision()
        data class Fire(val boundaryMs: Long) : TriggerDecision()
    }

    /**
     * Computes the next real-world clock boundary strictly after [afterEpochMs]
     * for [intervalMinutes] in [zoneId].
     *
     * For intervals dividing 60 (1, 2, 3, 4, 5, 6, 10, 12, 15, 20, 30, 60) or
     * exact multiples of 60 (120, 180, ...), alignment within the hour/day is
     * guaranteed even across DST transitions.
     */
    fun nextBoundaryMillis(
        intervalMinutes: Int,
        afterEpochMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val mins = intervalMinutes.coerceIn(1, 1440)
        val zdt = Instant.ofEpochMilli(afterEpochMs).atZone(zoneId)
        val dayStart = zdt.toLocalDate().atStartOfDay(zoneId)

        val candidate: ZonedDateTime = if (60 % mins == 0) {
            val hourStart = zdt.truncatedTo(ChronoUnit.HOURS)
            val currentMin = zdt.minute
            val nextSlotMin = ((currentMin / mins) + 1) * mins
            var c = hourStart.plusMinutes(nextSlotMin.toLong())
            while (c.toInstant().toEpochMilli() <= afterEpochMs) {
                c = c.plusMinutes(mins.toLong())
            }
            c
        } else {
            val elapsedMins = ChronoUnit.MINUTES.between(dayStart, zdt).coerceAtLeast(0L)
            var slot = (elapsedMins / mins) + 1L
            var c = dayStart.plusMinutes(slot * mins)
            while (c.toInstant().toEpochMilli() <= afterEpochMs) {
                slot++
                c = dayStart.plusMinutes(slot * mins)
            }
            c
        }
        return candidate.toInstant().toEpochMilli()
    }

    /**
     * Computes the most recent clock boundary at or before [atEpochMs] for [intervalMinutes].
     * Used only for UI progress-bar interpolation between previous and next boundary.
     */
    fun previousBoundaryMillis(
        intervalMinutes: Int,
        atEpochMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val mins = intervalMinutes.coerceIn(1, 1440)
        val zdt = Instant.ofEpochMilli(atEpochMs).atZone(zoneId)
        return if (60 % mins == 0) {
            val hourStart = zdt.truncatedTo(ChronoUnit.HOURS)
            val prevSlotMin = (zdt.minute / mins) * mins
            val c = hourStart.plusMinutes(prevSlotMin.toLong())
            val ms = c.toInstant().toEpochMilli()
            if (ms <= atEpochMs) ms else c.minusMinutes(mins.toLong()).toInstant().toEpochMilli()
        } else {
            val dayStart = zdt.toLocalDate().atStartOfDay(zoneId)
            val elapsedMins = ChronoUnit.MINUTES.between(dayStart, zdt).coerceAtLeast(0L)
            val slot = elapsedMins / mins
            val c = dayStart.plusMinutes(slot * mins)
            val ms = c.toInstant().toEpochMilli()
            if (ms <= atEpochMs) ms else dayStart.plusMinutes((slot - 1).coerceAtLeast(0) * mins.toLong()).toInstant().toEpochMilli()
        }
    }

    /** Returns true if [boundaryEpochMs] lies exactly on a wall-clock boundary for [intervalMinutes]. */
    fun isOnBoundary(
        intervalMinutes: Int,
        boundaryEpochMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        val mins = intervalMinutes.coerceIn(1, 1440)
        val zdt = Instant.ofEpochMilli(boundaryEpochMs).atZone(zoneId)
        if (zdt.second != 0 || zdt.nano != 0) return false
        return if (60 % mins == 0) {
            zdt.minute % mins == 0
        } else {
            val dayStart = zdt.toLocalDate().atStartOfDay(zoneId)
            val elapsedMins = ChronoUnit.MINUTES.between(dayStart, zdt)
            elapsedMins >= 0 && elapsedMins % mins == 0L
        }
    }

    /**
     * Finds the earliest upcoming global clock boundary across all enabled intervals,
     * and resolves the single highest-priority interval when multiple intervals coincide.
     */
    fun nextGlobalEvent(
        intervals: List<RecallInterval>,
        afterEpochMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): ScheduledChime? {
        val enabled = intervals.filter { it.enabled && it.minutes >= 1 }
        if (enabled.isEmpty()) return null

        val withNext = enabled.map { interval ->
            interval to nextBoundaryMillis(interval.minutes, afterEpochMs, zoneId)
        }
        val earliestBoundary = withNext.minOf { it.second }
        val coinciding = withNext.filter { it.second == earliestBoundary }.map { it.first }
        val winner = resolvePriorityWinner(coinciding) ?: return null
        return ScheduledChime(
            boundaryEpochMs = earliestBoundary,
            winningInterval = winner,
            coincidingIntervals = coinciding.sortedWith(priorityComparator),
        )
    }

    /** Resolves which enabled interval should chime at a specific [boundaryEpochMs]. */
    fun resolveAtBoundary(
        intervals: List<RecallInterval>,
        boundaryEpochMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): ScheduledChime? {
        val coinciding = intervals.filter { it.enabled && it.minutes >= 1 && isOnBoundary(it.minutes, boundaryEpochMs, zoneId) }
        val winner = resolvePriorityWinner(coinciding) ?: return null
        return ScheduledChime(
            boundaryEpochMs = boundaryEpochMs,
            winningInterval = winner,
            coincidingIntervals = coinciding.sortedWith(priorityComparator),
        )
    }

    fun resolvePriorityWinner(coinciding: List<RecallInterval>): RecallInterval? =
        coinciding.minWithOrNull(priorityComparator)

    private val priorityComparator: Comparator<RecallInterval> =
        compareByDescending<RecallInterval> { it.priority }
            .thenByDescending { it.minutes }
            .thenBy { it.id }

    /**
     * Evaluates an incoming alarm trigger against active state, early tolerance,
     * duplicate suppression, and missed-event backlog prevention.
     */
    fun evaluateAlarmTrigger(
        targetBoundaryMs: Long,
        nowMs: Long,
        lastFiredBoundaryMs: Long,
        status: RecallStatus,
    ): TriggerDecision {
        if (status != RecallStatus.ACTIVE) return TriggerDecision.NotActive
        if (targetBoundaryMs <= 0L) return TriggerDecision.MissedSuppressed(0L, nowMs)
        if (nowMs < targetBoundaryMs - EARLY_TOLERANCE_MS) {
            return TriggerDecision.TooEarly(rescheduleFromMs = nowMs)
        }
        if (nowMs > targetBoundaryMs + MISSED_GRACE_MS) {
            return TriggerDecision.MissedSuppressed(
                lateByMs = nowMs - targetBoundaryMs,
                rescheduleFromMs = nowMs,
            )
        }
        if (targetBoundaryMs <= lastFiredBoundaryMs) {
            return TriggerDecision.Duplicate(rescheduleFromMs = maxOf(nowMs, targetBoundaryMs))
        }
        return TriggerDecision.Fire(boundaryMs = targetBoundaryMs)
    }
}
