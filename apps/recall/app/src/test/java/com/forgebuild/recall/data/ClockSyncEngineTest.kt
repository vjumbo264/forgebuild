package com.forgebuild.recall.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ClockSyncEngineTest {

    private val utc = ZoneId.of("UTC")
    private val ny = ZoneId.of("America/New_York")

    private fun epoch(hour: Int, minute: Int, second: Int = 0, millis: Int = 0, zone: ZoneId = utc): Long =
        ZonedDateTime.of(2026, 11, 20, hour, minute, second, millis * 1_000_000, zone)
            .toInstant().toEpochMilli()

    @Test
    fun scenario1_activateAt647pm_firstEventAt650pmFiveMinute() {
        val intervals = RecallDefaults.defaultIntervals()
        val now = epoch(18, 47, 15)
        val next = ClockSyncEngine.nextGlobalEvent(intervals, now, utc)
        assertNotNull(next)
        assertEquals(epoch(18, 50, 0), next!!.boundaryEpochMs)
        assertEquals("default_5m", next.winningInterval.id)
    }

    @Test
    fun scenario2_activateAt655pm_nextEventIsHourlyAt700pm() {
        val intervals = RecallDefaults.defaultIntervals()
        val now = epoch(18, 55, 0)
        val next = ClockSyncEngine.nextGlobalEvent(intervals, now, utc)
        assertNotNull(next)
        assertEquals(epoch(19, 0, 0), next!!.boundaryEpochMs)
        assertEquals("default_60m", next.winningInterval.id)
        assertEquals(3, next.coincidingIntervals.size)
    }

    @Test
    fun scenario3_thirtyMinuteOverridesFiveMinuteAt730pm() {
        val intervals = RecallDefaults.defaultIntervals()
        val now = epoch(19, 25, 30)
        val next = ClockSyncEngine.nextGlobalEvent(intervals, now, utc)
        assertNotNull(next)
        assertEquals(epoch(19, 30, 0), next!!.boundaryEpochMs)
        assertEquals("default_30m", next.winningInterval.id)
        assertEquals(listOf("default_30m", "default_5m"), next.coincidingIntervals.map { it.id })
    }

    @Test
    fun scenario4_custom15mAlignsWithRealClockAndOverrides5m() {
        val custom15 = RecallInterval(
            id = "custom_15m",
            label = "Quarter hour",
            minutes = 15,
            enabled = true,
            sound = RecallSound.SONAR_PING,
            volume = 0.9f,
            priority = RecallInterval.PRIORITY_CUSTOM,
        )
        val intervals = RecallDefaults.defaultIntervals() + custom15

        // Activated at 6:08 PM -> 6:10 is 5m, 6:15 is 15m (overrides 5m), 6:30 is 30m (overrides 15m & 5m)
        val at610 = ClockSyncEngine.nextGlobalEvent(intervals, epoch(18, 8), utc)!!
        assertEquals(epoch(18, 10), at610.boundaryEpochMs)
        assertEquals("default_5m", at610.winningInterval.id)

        val at615 = ClockSyncEngine.nextGlobalEvent(intervals, at610.boundaryEpochMs, utc)!!
        assertEquals(epoch(18, 15), at615.boundaryEpochMs)
        assertEquals("custom_15m", at615.winningInterval.id)

        val at630 = ClockSyncEngine.nextGlobalEvent(intervals, epoch(18, 25), utc)!!
        assertEquals(epoch(18, 30), at630.boundaryEpochMs)
        assertEquals("default_30m", at630.winningInterval.id)
    }

    @Test
    fun fullSequenceFrom647To730MatchesSpec() {
        val intervals = RecallDefaults.defaultIntervals()
        var cursor = epoch(18, 47)
        val expected = listOf(
            epoch(18, 50) to "default_5m",
            epoch(18, 55) to "default_5m",
            epoch(19, 0) to "default_60m",
            epoch(19, 5) to "default_5m",
            epoch(19, 10) to "default_5m",
            epoch(19, 15) to "default_5m",
            epoch(19, 20) to "default_5m",
            epoch(19, 25) to "default_5m",
            epoch(19, 30) to "default_30m",
        )
        for ((expectedTime, expectedId) in expected) {
            val ev = ClockSyncEngine.nextGlobalEvent(intervals, cursor, utc)!!
            assertEquals(expectedTime, ev.boundaryEpochMs)
            assertEquals(expectedId, ev.winningInterval.id)
            cursor = ev.boundaryEpochMs
        }
    }

    @Test
    fun scenario12_missedEventsAreSuppressedNeverReplayed() {
        val target = epoch(18, 50, 0)
        // Delivered 12 minutes late (phone was off or deep-dozed)
        val lateNow = epoch(19, 2, 0)
        val decision = ClockSyncEngine.evaluateAlarmTrigger(
            targetBoundaryMs = target,
            nowMs = lateNow,
            lastFiredBoundaryMs = 0L,
            status = RecallStatus.ACTIVE,
        )
        assertTrue(decision is ClockSyncEngine.TriggerDecision.MissedSuppressed)
        val nextAfterSkip = ClockSyncEngine.nextGlobalEvent(
            RecallDefaults.defaultIntervals(),
            (decision as ClockSyncEngine.TriggerDecision.MissedSuppressed).rescheduleFromMs,
            utc,
        )!!
        assertEquals(epoch(19, 5, 0), nextAfterSkip.boundaryEpochMs)
    }

    @Test
    fun duplicateAndEarlyTriggerGuards() {
        val target = epoch(18, 50, 0)

        // Exact duplicate for same boundary
        val dup = ClockSyncEngine.evaluateAlarmTrigger(
            targetBoundaryMs = target,
            nowMs = target + 500,
            lastFiredBoundaryMs = target,
            status = RecallStatus.ACTIVE,
        )
        assertTrue(dup is ClockSyncEngine.TriggerDecision.Duplicate)

        // Premature trigger (10s early -> TooEarly)
        val early = ClockSyncEngine.evaluateAlarmTrigger(
            targetBoundaryMs = target,
            nowMs = target - 10_000L,
            lastFiredBoundaryMs = 0L,
            status = RecallStatus.ACTIVE,
        )
        assertTrue(early is ClockSyncEngine.TriggerDecision.TooEarly)

        // Slightly early within 2s tolerance -> Fire, and next boundary advances past target
        val slightEarly = ClockSyncEngine.evaluateAlarmTrigger(
            targetBoundaryMs = target,
            nowMs = target - 400L,
            lastFiredBoundaryMs = 0L,
            status = RecallStatus.ACTIVE,
        )
        assertEquals(ClockSyncEngine.TriggerDecision.Fire(target), slightEarly)
        val next = ClockSyncEngine.nextGlobalEvent(
            RecallDefaults.defaultIntervals(),
            maxOf(target - 400L, target),
            utc,
        )!!
        assertEquals(epoch(18, 55, 0), next.boundaryEpochMs)
    }

    @Test
    fun pausedOrStoppedIgnoresTrigger() {
        val target = epoch(18, 50, 0)
        assertEquals(
            ClockSyncEngine.TriggerDecision.NotActive,
            ClockSyncEngine.evaluateAlarmTrigger(target, target, 0L, RecallStatus.PAUSED),
        )
        assertEquals(
            ClockSyncEngine.TriggerDecision.NotActive,
            ClockSyncEngine.evaluateAlarmTrigger(target, target, 0L, RecallStatus.STOPPED),
        )
    }

    @Test
    fun timezoneOffsetUsesLocalClockMinutes() {
        // Asia/Kolkata is UTC+5:30! Hourly chime must hit :00 local (e.g. 19:00 IST = 13:30 UTC)
        val ist = ZoneId.of("Asia/Kolkata")
        val nowIst = ZonedDateTime.of(2026, 11, 20, 18, 55, 0, 0, ist).toInstant().toEpochMilli()
        val ev = ClockSyncEngine.nextGlobalEvent(RecallDefaults.defaultIntervals(), nowIst, ist)!!
        val expectedIstHour = ZonedDateTime.of(2026, 11, 20, 19, 0, 0, 0, ist).toInstant().toEpochMilli()
        assertEquals(expectedIstHour, ev.boundaryEpochMs)
        assertEquals("default_60m", ev.winningInterval.id)
    }

    @Test
    fun repositoryPersistenceAndIntervalCrud() {
        val storage = InMemoryRecallStorage()
        val repo1 = RecallRepository(storage)
        assertEquals(3, repo1.currentState.intervals.size)
        assertEquals(RecallStatus.STOPPED, repo1.currentState.status)

        repo1.setStatus(RecallStatus.ACTIVE)
        repo1.addInterval("Quarter Hour", 15, RecallSound.WESTMINSTER_CUE, 0.55f, 60)
        repo1.toggleInterval("default_5m", false)
        repo1.recordFiredBoundary(epoch(18, 50))
        repo1.setHeadsUpNotifications(false)

        // Reload from persisted JSON
        val repo2 = RecallRepository(storage)
        val loaded = repo2.currentState
        assertEquals(RecallStatus.ACTIVE, loaded.status)
        assertEquals(4, loaded.intervals.size)
        assertEquals(false, loaded.intervals.first { it.id == "default_5m" }.enabled)
        val added = loaded.intervals.first { it.minutes == 15 }
        assertEquals("Quarter Hour", added.label)
        assertEquals(RecallSound.WESTMINSTER_CUE, added.sound)
        assertEquals(0.55f, added.volume, 0.001f)
        assertEquals(60, added.priority)
        assertEquals(epoch(18, 50), loaded.lastFiredBoundaryMs)
        assertEquals(false, loaded.headsUpNotificationsEnabled)

        // Disabling all intervals yields null nextGlobalEvent
        loaded.intervals.forEach { repo2.toggleInterval(it.id, false) }
        assertNull(ClockSyncEngine.nextGlobalEvent(repo2.currentState.intervals, epoch(18, 47), utc))
    }
}
