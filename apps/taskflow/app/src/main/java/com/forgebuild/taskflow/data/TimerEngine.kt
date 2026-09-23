package com.forgebuild.taskflow.data

/**
 * Pure countdown state logic for the per-task timer (play / pause / extend / complete).
 * Persisted state lives on [Task]: [Task.timerEndsAt] (running), [Task.timerRemainingMs]
 * (paused) and [Task.timerFinished] (elapsed, awaiting extend/complete). No DB access here.
 */
object TimerEngine {
    enum class TimerState { IDLE, RUNNING, PAUSED, FINISHED }

    fun stateOf(t: Task, nowMs: Long = System.currentTimeMillis()): TimerState = when {
        t.timerFinished -> TimerState.FINISHED
        t.timerRemainingMs != null -> TimerState.PAUSED
        t.timerEndsAt != null -> if (t.timerEndsAt > nowMs) TimerState.RUNNING else TimerState.FINISHED
        else -> TimerState.IDLE
    }

    /** Milliseconds left right now: ticking for RUNNING, frozen for PAUSED, 0 for FINISHED. */
    fun remainingMs(t: Task, nowMs: Long = System.currentTimeMillis()): Long = when (stateOf(t, nowMs)) {
        TimerState.RUNNING -> (t.timerEndsAt!! - nowMs).coerceAtLeast(0L)
        TimerState.PAUSED -> t.timerRemainingMs!!.coerceAtLeast(0L)
        TimerState.FINISHED -> 0L
        TimerState.IDLE -> t.durationMinutes.coerceAtLeast(1L) * 60_000L
    }

    /** Progress 0..1 of the total duration already consumed (for a progress arc). */
    fun progress(t: Task, nowMs: Long = System.currentTimeMillis()): Float {
        val total = t.durationMinutes.coerceAtLeast(1L) * 60_000L
        val rem = remainingMs(t, nowMs).coerceAtMost(total)
        return ((total - rem).toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    fun startOrResume(t: Task, nowMs: Long = System.currentTimeMillis()): Task = when (stateOf(t, nowMs)) {
        TimerState.IDLE -> t.copy(timerEndsAt = nowMs + t.durationMinutes.coerceAtLeast(1L) * 60_000L,
            timerRemainingMs = null, timerFinished = false, updatedAt = nowMs)
        TimerState.PAUSED -> t.copy(timerEndsAt = nowMs + t.timerRemainingMs!!.coerceAtLeast(1L),
            timerRemainingMs = null, timerFinished = false, updatedAt = nowMs)
        // Re-running a finished (or already running) timer restarts a full countdown.
        else -> t.copy(timerEndsAt = nowMs + t.durationMinutes.coerceAtLeast(1L) * 60_000L,
            timerRemainingMs = null, timerFinished = false, updatedAt = nowMs)
    }

    fun pause(t: Task, nowMs: Long = System.currentTimeMillis()): Task =
        if (stateOf(t, nowMs) == TimerState.RUNNING)
            t.copy(timerRemainingMs = (t.timerEndsAt!! - nowMs).coerceAtLeast(1L),
                timerEndsAt = null, timerFinished = false, updatedAt = nowMs)
        else t

    /** Add [extraMs] to the countdown — works mid-run, while paused, or after it finished. */
    fun extend(t: Task, extraMs: Long, nowMs: Long = System.currentTimeMillis()): Task =
        when (stateOf(t, nowMs)) {
            TimerState.RUNNING -> t.copy(timerEndsAt = t.timerEndsAt!! + extraMs, updatedAt = nowMs)
            TimerState.PAUSED -> t.copy(timerRemainingMs = t.timerRemainingMs!! + extraMs, updatedAt = nowMs)
            // Finished: a fresh countdown of exactly the extension, already ticking.
            TimerState.FINISHED -> t.copy(timerEndsAt = nowMs + extraMs, timerRemainingMs = null,
                timerFinished = false, updatedAt = nowMs)
            // Idle: start ticking with the full duration plus the extension.
            TimerState.IDLE -> t.copy(timerEndsAt = nowMs + t.durationMinutes.coerceAtLeast(1L) * 60_000L + extraMs,
                timerRemainingMs = null, timerFinished = false, updatedAt = nowMs)
        }

    /** Clear all timer state (used when a task is completed/deleted/reset). */
    fun clear(t: Task): Task = t.copy(timerEndsAt = null, timerRemainingMs = null, timerFinished = false)
}
