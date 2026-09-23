package me.timeto.shared

import kotlinx.coroutines.runBlocking
import me.timeto.shared.db.KvDb
import me.timeto.shared.vm.history.DaysUiUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HistoryDaysUiTest {

    @Test
    fun grouping_beforeInsideAfter() = runBlocking {
        initTestDb()
        insertActivitySq(id = 1)

        val today = UnixTime().localDay
        val yesterday = today - 1
        val p = UnixTime.byLocalDay(today - 2).time + 43_200 // noon, day-2
        val i1 = UnixTime.byLocalDay(yesterday).time + 3_600
        val i2 = UnixTime.byLocalDay(yesterday).time + 7_200
        val a = UnixTime.byLocalDay(today + 1).time + 3_600 // tomorrow

        insertIntervalSq(id = 1, time = p)
        insertIntervalSq(id = 2, time = i1)
        insertIntervalSq(id = 3, time = i2)
        insertIntervalSq(id = 4, time = a)
        refreshCache()

        val daysUi = DaysUiUtils.selectDaysUi(firstDay = yesterday, lastDay = today)

        // Day-2 has a group internally but is outside the window
        assertEquals(listOf(yesterday, today), daysUi.map { it.unixDay })

        val yesterdayUi = daysUi.first { it.unixDay == yesterday }
        assertEquals(listOf(1, 2, 3), yesterdayUi.intervalsDb.map { it.id })
        assertEquals(a, yesterdayUi.nextIntervalTimeStart)
        assertTrue(yesterdayUi.intervalsUi[0].isStartsPrevDay)
        assertFalse(yesterdayUi.intervalsUi[1].isStartsPrevDay)

        // Today has no interval of its own: the before/after branch
        // carries yesterday's last interval, finished by tomorrow's row.
        val todayUi = daysUi.first { it.unixDay == today }
        assertEquals(listOf(3), todayUi.intervalsDb.map { it.id })
        assertEquals(a, todayUi.nextIntervalTimeStart)
        assertTrue(todayUi.intervalsUi[0].isStartsPrevDay)
    }

    @Test
    fun crossMidnight_joinsPrevIntervalIntoNextDay() = runBlocking {
        initTestDb()
        insertActivitySq(id = 1)

        val today = UnixTime().localDay
        val yesterday = today - 1
        val todayStart = UnixTime.byLocalDay(today).time
        val i = todayStart - 600 // 23:50 yesterday
        val j = todayStart + 600 // 00:10 today

        insertIntervalSq(id = 1, time = i)
        insertIntervalSq(id = 2, time = j)
        refreshCache()

        val daysUi = DaysUiUtils.selectDaysUi(firstDay = yesterday, lastDay = today)
        val todayUi = daysUi.first { it.unixDay == today }

        // Yesterday's tail joins today's group
        assertEquals(listOf(1, 2), todayUi.intervalsDb.map { it.id })
        val prevUi = todayUi.intervalsUi[0]
        assertTrue(prevUi.isStartsPrevDay)
        assertEquals("20 min", prevUi.periodString) // real duration j - i
        // Bar is clipped at midnight: only 600s drawn inside today
        assertEquals(600, prevUi.secondsForBar)
        assertEquals(j, prevUi.barTimeFinish)
    }

    @Test
    fun crossMidnight_noTailWhenIntervalStartsAtMidnight() = runBlocking {
        initTestDb()
        insertActivitySq(id = 1)

        val today = UnixTime().localDay
        val todayStart = UnixTime.byLocalDay(today).time

        insertIntervalSq(id = 1, time = todayStart - 600)
        insertIntervalSq(id = 2, time = todayStart) // exactly 00:00
        refreshCache()

        val daysUi = DaysUiUtils.selectDaysUi(firstDay = today, lastDay = today)
        val todayUi = daysUi.first { it.unixDay == today }

        // 00:00 start drops the previous-day tail entirely
        assertEquals(listOf(2), todayUi.intervalsDb.map { it.id })
    }

    @Test
    fun dayStartOffset_ignoredByHistoryAndSummaryBars(): Unit = runBlocking {
        initTestDb()
        insertActivitySq(id = 1)

        // Day starts at 02:00 -> an interval at 01:00 belongs to
        // "yesterday" by V30 math, but history/summary use raw local day.
        KvDb.KEY.DAY_START_OFFSET_SECONDS.upsertInt(7_200)
        refreshCache()

        val today = UnixTime().localDay
        val intervalTime = UnixTime.byLocalDay(today).time + 3_600 // 01:00
        insertIntervalSq(id = 1, time = intervalTime)
        refreshCache()

        // Sanity: the day-start-aware calc really does disagree
        assertEquals(
            today - 1,
            DayStartOffsetUtils.calcDay(time = intervalTime, dayStartOffsetSeconds = 7_200),
        )

        // History groups by raw local day
        val daysUi = DaysUiUtils.selectDaysUi(firstDay = today, lastDay = today)
        val todayUi = daysUi.first { it.unixDay == today }
        assertEquals(listOf(1), todayUi.intervalsDb.map { it.id })

        // Summary bars group by raw local day too
        val dayBarsUi = DayBarsUi.buildList(
            dayStart = today,
            dayFinish = today,
            utcOffset = localUtcOffset,
        ).first()
        assertNotNull(dayBarsUi.barsUi.firstOrNull { it.intervalDb?.id == 1 })
    }
}
