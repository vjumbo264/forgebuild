package com.forgebuild.taskflow.data

/**
 * Pure countdown state logic for the per-task timer (play / pause / extend / complete),
 * extended in Revision Pass 7 with the Pomodoro pattern.
 *
 * Persisted state lives on [Task]: [Task.timerEndsAt] (running wall-clock end),
 * [Task.timerStartedAt] (wall-clock start of the current run — needed to map wall time
 * onto the Pomodoro plan), [Task.timerRemainingMs] (chargeable ms held while PAUSED),
 * [Task.timerFinished] (elapsed, awaiting extend/complete) and the Pomodoro snapshot
 * [Task.pomodoroWorkMs]/[Task.pomodoroBreakMs] (null = plain continuous countdown).
 *
 * Pomodoro model (operator spec, Revision Pass 7):
 *  - The task's duration is split into n = floor(duration / work) full work intervals.
 *  - Between two work intervals a FREE break runs: the task's own countdown pauses and
 *    resumes automatically when the break ends (breaks do not consume task duration).
 *  - After the final full work interval, if the leftover covers at least one break, that
 *    break runs (consuming duration), then any remaining leftover finishes as a normal
 *    non-Pomodoro countdown. A task shorter than one work interval never gets Pomodoro.
 *  - Examples that pin this down: 25/5 on a 30m task → one work interval + its break,
 *    then complete. 10/5 on a 30m task → three work intervals (10+10+10 = 30m of work)
 *    with free breaks between them and none after the final one.
 */
object TimerEngine {
    enum class TimerState { IDLE, RUNNING, PAUSED, FINISHED }

    /**
     * One wall-clock segment of a Pomodoro plan.
     * [chargeable] segments consume the task's duration (all work segments + the trailing
     * break that fits inside the duration); free middle breaks are not chargeable.
     */
    data class Segment(
        val isWork: Boolean,
        val wallStartMs: Long,
        val wallDurationMs: Long,
        val chargeable: Boolean
    )

    /** Live info about the segment the timer is currently inside (for UI/notification). */
    data class SegmentInfo(val isWork: Boolean, val progress: Float, val segmentRemainingMs: Long)

    fun isPomodoro(t: Task): Boolean =
        (t.pomodoroWorkMs ?: 0L) > 0L && (t.pomodoroBreakMs ?: 0L) > 0L

    fun stateOf(t: Task, nowMs: Long = System.currentTimeMillis()): TimerState = when {
        t.timerFinished -> TimerState.FINISHED
        t.timerRemainingMs != null -> TimerState.PAUSED
        t.timerEndsAt != null -> if (t.timerEndsAt > nowMs) TimerState.RUNNING else TimerState.FINISHED
        else -> TimerState.IDLE
    }

    private fun totalWorkMs(t: Task): Long = t.durationMinutes.coerceAtLeast(1L) * 60_000L

    /** Build the Pomodoro wall-clock plan described in the class KDoc. */
    fun buildPlan(totalWorkMs: Long, workMs: Long, breakMs: Long): List<Segment> {
        if (workMs <= 0L || totalWorkMs < workMs) {
            // Shorter than one full work interval: Pomodoro does not apply at all.
            return listOf(Segment(isWork = true, wallStartMs = 0L, wallDurationMs = totalWorkMs, chargeable = true))
        }
        val segs = mutableListOf<Segment>()
        var wall = 0L
        var workLeft = totalWorkMs
        var firstWork = true
        while (workLeft >= workMs) {
            if (!firstWork) {
                // Free break between two work intervals — does not consume duration.
                segs += Segment(isWork = false, wallStartMs = wall, wallDurationMs = breakMs, chargeable = false)
                wall += breakMs
            }
            segs += Segment(isWork = true, wallStartMs = wall, wallDurationMs = workMs, chargeable = true)
            wall += workMs
            workLeft -= workMs
            firstWork = false
        }
        // Leftover too short for another full work interval: a final break that fits runs
        // (consuming duration), then the rest finishes as a normal countdown.
        if (workLeft >= breakMs) {
            segs += Segment(isWork = false, wallStartMs = wall, wallDurationMs = breakMs, chargeable = true)
            wall += breakMs
            workLeft -= breakMs
        }
        if (workLeft > 0L) {
            segs += Segment(isWork = true, wallStartMs = wall, wallDurationMs = workLeft, chargeable = true)
        }
        return segs
    }

    private fun planOf(t: Task): List<Segment>? =
        if (isPomodoro(t)) buildPlan(totalWorkMs(t), t.pomodoroWorkMs!!, t.pomodoroBreakMs!!) else null

    private fun planWallEnd(plan: List<Segment>): Long =
        plan.last().let { it.wallStartMs + it.wallDurationMs }

    /** Wall offset (from run start) at which [doneMs] chargeable ms have elapsed. */
    private fun wallOffsetForChargeableDone(plan: List<Segment>, doneMs: Long): Long {
        var done = doneMs.coerceAtLeast(0L)
        var off = 0L
        for (s in plan) {
            if (s.chargeable) {
                val c = minOf(done, s.wallDurationMs)
                off = s.wallStartMs + c
                done -= c
                if (done <= 0L) break
            } else {
                off = s.wallStartMs + s.wallDurationMs
            }
        }
        return off
    }

    /** Milliseconds left right now: ticking for RUNNING, frozen for PAUSED, 0 for FINISHED. */
    fun remainingMs(t: Task, nowMs: Long = System.currentTimeMillis()): Long = when (stateOf(t, nowMs)) {
        TimerState.RUNNING -> {
            val plan = planOf(t)
            if (plan == null) {
                (t.timerEndsAt!! - nowMs).coerceAtLeast(0L)
            } else {
                val start = t.timerStartedAt
                if (start == null) {
                    (t.timerEndsAt!! - nowMs).coerceAtLeast(0L)
                } else {
                    val wallElapsed = (nowMs - start).coerceAtLeast(0L)
                    var done = 0L
                    for (s in plan) {
                        if (s.chargeable) done += (wallElapsed - s.wallStartMs).coerceIn(0L, s.wallDurationMs)
                    }
                    // Extension time beyond the plan runs as a plain tail after the plan ends.
                    val extra = (t.timerEndsAt!! - (start + planWallEnd(plan))).coerceAtLeast(0L)
                    (totalWorkMs(t) - done).coerceAtLeast(0L) + extra
                }
            }
        }
        TimerState.PAUSED -> t.timerRemainingMs!!.coerceAtLeast(0L)
        TimerState.FINISHED -> 0L
        TimerState.IDLE -> totalWorkMs(t)
    }

    /** Progress 0..1 of the total duration already consumed (for a progress arc). */
    fun progress(t: Task, nowMs: Long = System.currentTimeMillis()): Float {
        val total = totalWorkMs(t)
        val rem = remainingMs(t, nowMs).coerceAtMost(total)
        return ((total - rem).toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * The Pomodoro segment the timer is currently inside (null for plain countdowns or
     * when past the plan on an extension tail). Drives the "progress toward the next
     * transition" indicator: toward the upcoming break while working, toward resuming
     * work while on a break.
     */
    fun currentSegment(t: Task, nowMs: Long = System.currentTimeMillis()): SegmentInfo? {
        if (!isPomodoro(t)) return null
        val plan = planOf(t) ?: return null
        val start = t.timerStartedAt ?: return null
        val wall = (nowMs - start).coerceAtLeast(0L)
        for (s in plan) {
            if (wall >= s.wallStartMs && wall < s.wallStartMs + s.wallDurationMs) {
                return SegmentInfo(
                    isWork = s.isWork,
                    progress = ((wall - s.wallStartMs).toFloat() / s.wallDurationMs.toFloat()).coerceIn(0f, 1f),
                    segmentRemainingMs = s.wallStartMs + s.wallDurationMs - wall
                )
            }
        }
        return null
    }

    fun startOrResume(
        t: Task,
        nowMs: Long = System.currentTimeMillis(),
        pomodoroWorkMs: Long? = null,
        pomodoroBreakMs: Long? = null
    ): Task = when (stateOf(t, nowMs)) {
        TimerState.PAUSED -> {
            val r = t.timerRemainingMs!!.coerceAtLeast(1L)
            if (isPomodoro(t)) {
                val plan = planOf(t)!!
                val total = totalWorkMs(t)
                val done = (total - minOf(r, total)).coerceAtLeast(0L)
                val off = wallOffsetForChargeableDone(plan, done)
                val beyond = (r - total).coerceAtLeast(0L)
                t.copy(
                    timerStartedAt = nowMs - off,
                    timerEndsAt = nowMs + (planWallEnd(plan) - off) + beyond,
                    timerRemainingMs = null, timerFinished = false, updatedAt = nowMs
                )
            } else {
                t.copy(
                    timerEndsAt = nowMs + r,
                    timerRemainingMs = null, timerFinished = false, updatedAt = nowMs
                )
            }
        }
        // IDLE / FINISHED / re-pressing play while RUNNING: start a fresh full countdown.
        else -> {
            val total = totalWorkMs(t)
            val usePomo = pomodoroWorkMs != null && pomodoroBreakMs != null && total >= pomodoroWorkMs
            if (usePomo) {
                val plan = buildPlan(total, pomodoroWorkMs!!, pomodoroBreakMs!!)
                t.copy(
                    pomodoroWorkMs = pomodoroWorkMs, pomodoroBreakMs = pomodoroBreakMs,
                    timerStartedAt = nowMs, timerEndsAt = nowMs + planWallEnd(plan),
                    timerRemainingMs = null, timerFinished = false, updatedAt = nowMs
                )
            } else {
                t.copy(
                    pomodoroWorkMs = null, pomodoroBreakMs = null, timerStartedAt = null,
                    timerEndsAt = nowMs + total,
                    timerRemainingMs = null, timerFinished = false, updatedAt = nowMs
                )
            }
        }
    }

    fun pause(t: Task, nowMs: Long = System.currentTimeMillis()): Task =
        if (stateOf(t, nowMs) == TimerState.RUNNING)
            t.copy(
                timerRemainingMs = remainingMs(t, nowMs).coerceAtLeast(1L),
                timerEndsAt = null, timerStartedAt = null, timerFinished = false, updatedAt = nowMs
            )
        else t

    /** Add [extraMs] to the countdown — works mid-run, while paused, or after it finished. */
    fun extend(t: Task, extraMs: Long, nowMs: Long = System.currentTimeMillis()): Task =
        when (stateOf(t, nowMs)) {
            TimerState.RUNNING -> {
                if (isPomodoro(t)) {
                    // Re-anchor: extended work beyond the plan runs as a plain tail.
                    val plan = planOf(t)!!
                    val total = totalWorkMs(t)
                    val r = remainingMs(t, nowMs) + extraMs
                    val done = (total - minOf(r, total)).coerceAtLeast(0L)
                    val off = wallOffsetForChargeableDone(plan, done)
                    val beyond = (r - total).coerceAtLeast(0L)
                    t.copy(
                        timerStartedAt = nowMs - off,
                        timerEndsAt = nowMs + (planWallEnd(plan) - off) + beyond,
                        updatedAt = nowMs
                    )
                } else {
                    t.copy(timerEndsAt = t.timerEndsAt!! + extraMs, updatedAt = nowMs)
                }
            }
            TimerState.PAUSED -> t.copy(timerRemainingMs = t.timerRemainingMs!! + extraMs, updatedAt = nowMs)
            TimerState.FINISHED -> {
                // Fresh countdown of exactly the extension, already ticking. Pomodoro
                // applies to the extension when it fits at least one full work interval.
                val w = t.pomodoroWorkMs
                val b = t.pomodoroBreakMs
                if (w != null && b != null && extraMs >= w) {
                    val plan = buildPlan(extraMs, w, b)
                    t.copy(
                        timerStartedAt = nowMs, timerEndsAt = nowMs + planWallEnd(plan),
                        timerRemainingMs = null, timerFinished = false, updatedAt = nowMs
                    )
                } else {
                    t.copy(
                        pomodoroWorkMs = null, pomodoroBreakMs = null, timerStartedAt = null,
                        timerEndsAt = nowMs + extraMs,
                        timerRemainingMs = null, timerFinished = false, updatedAt = nowMs
                    )
                }
            }
            TimerState.IDLE -> t.copy(
                pomodoroWorkMs = null, pomodoroBreakMs = null, timerStartedAt = null,
                timerEndsAt = nowMs + totalWorkMs(t) + extraMs,
                timerRemainingMs = null, timerFinished = false, updatedAt = nowMs
            )
        }

    /** Clear all timer state (used when a task is completed/deleted/reset). */
    fun clear(t: Task): Task = t.copy(
        timerEndsAt = null, timerRemainingMs = null, timerFinished = false,
        timerStartedAt = null, pomodoroWorkMs = null, pomodoroBreakMs = null
    )
}
