package com.forgebuild.taskflow.data

import java.util.Calendar

/** Computes the next occurrence of a recurring template, preserving time-of-day. */
object RecurrenceEngine {
    fun nextAfter(template: Task, fromMillis: Long): Long? {
        if (template.recurrence == Recurrence.NONE) return null
        val base = template.fixedTime ?: fromMillis
        val from = Calendar.getInstance().apply { timeInMillis = fromMillis }
        val cal = Calendar.getInstance().apply { timeInMillis = maxOf(base, fromMillis) }
        return when (template.recurrence) {
            Recurrence.NONE -> null
            Recurrence.DAILY -> rollDays(cal, from)
            Recurrence.WEEKLY -> nextWeekly(cal, from, template.weekdaysMask)
            Recurrence.MONTHLY -> rollMonths(cal, from)
            Recurrence.YEARLY -> rollYears(cal, from)
        }
    }

    private fun timeOfDay(src: Calendar): Triple<Int, Int, Int> =
        Triple(src.get(Calendar.HOUR_OF_DAY), src.get(Calendar.MINUTE), src.get(Calendar.SECOND))

    private fun withTime(day: Calendar, tod: Triple<Int, Int, Int>): Calendar = (day.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, tod.first); set(Calendar.MINUTE, tod.second)
        set(Calendar.SECOND, tod.third); set(Calendar.MILLISECOND, 0)
    }

    private fun rollDays(cal: Calendar, from: Calendar): Long {
        val d = withTime(cal, timeOfDay(cal))
        while (!d.after(from)) d.add(Calendar.DAY_OF_YEAR, 1)
        return d.timeInMillis
    }

    private fun rollMonths(cal: Calendar, from: Calendar): Long {
        val tod = timeOfDay(cal); val dom = cal.get(Calendar.DAY_OF_MONTH)
        val d = withTime(cal, tod)
        while (!d.after(from)) {
            d.add(Calendar.MONTH, 1)
            d.set(Calendar.DAY_OF_MONTH, minOf(dom, d.getActualMaximum(Calendar.DAY_OF_MONTH)))
        }
        return withTime(d, tod).timeInMillis
    }

    private fun rollYears(cal: Calendar, from: Calendar): Long {
        val tod = timeOfDay(cal)
        val d = withTime(cal, tod)
        while (!d.after(from)) d.add(Calendar.YEAR, 1)
        return d.timeInMillis
    }

    private fun nextWeekly(cal: Calendar, from: Calendar, mask: Int): Long? {
        val tod = timeOfDay(cal)
        if (mask == 0) {
            val d = withTime(cal, tod)
            while (!d.after(from)) d.add(Calendar.DAY_OF_YEAR, 7)
            return d.timeInMillis
        }
        val d = withTime(cal, tod)
        repeat(370) {
            if ((mask and calendarDayToBit(d.get(Calendar.DAY_OF_WEEK))) != 0 && d.after(from)) return d.timeInMillis
            d.add(Calendar.DAY_OF_YEAR, 1)
        }
        return null
    }

    fun calendarDayToBit(calendarDay: Int): Int = when (calendarDay) {
        Calendar.MONDAY -> 1 shl 0; Calendar.TUESDAY -> 1 shl 1; Calendar.WEDNESDAY -> 1 shl 2
        Calendar.THURSDAY -> 1 shl 3; Calendar.FRIDAY -> 1 shl 4; Calendar.SATURDAY -> 1 shl 5
        Calendar.SUNDAY -> 1 shl 6; else -> 0
    }

    val WEEKDAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
}
