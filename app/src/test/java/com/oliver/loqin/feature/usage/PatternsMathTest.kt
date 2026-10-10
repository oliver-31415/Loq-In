package com.oliver.loqin.feature.usage

import com.oliver.loqin.feature.usage.PatternsMath.PeakGroup
import com.oliver.loqin.feature.usage.PatternsMath.Span
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternsMathTest {

    private val utc = TimeZone.getTimeZone("UTC")

    // October 2026: the 5th is a Monday, the 10th a Saturday, the 11th a Sunday.
    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(2026, Calendar.OCTOBER, day, hour, minute, 0)
        }.timeInMillis

    private fun emptyGrid() = Array(PatternsMath.WEEKDAY_COUNT) { DoubleArray(PatternsMath.HOUR_COUNT) }

    @Test
    fun weekdayIndexIsMondayFirst() {
        val monday = Calendar.getInstance(utc).apply { timeInMillis = at(5, 12) }
        val sunday = Calendar.getInstance(utc).apply { timeInMillis = at(11, 12) }
        assertEquals(0, PatternsMath.weekdayIndex(monday))
        assertEquals(6, PatternsMath.weekdayIndex(sunday))
    }

    @Test
    fun overlapClipsToWindow() {
        val spans = listOf(Span(at(5, 9), at(5, 11)))
        assertEquals(60.0, PatternsMath.overlapMinutes(spans, at(5, 10), at(5, 12)), 0.001)
        assertEquals(0.0, PatternsMath.overlapMinutes(spans, at(5, 11), at(5, 12)), 0.001)
    }

    @Test
    fun heatmapSplitsSessionAtHourBoundary() {
        val grid = PatternsMath.heatmapAverages(
            spans = listOf(Span(at(5, 21, 30), at(5, 22, 30))),
            windowStartMs = at(5, 0),
            windowEndMs = at(6, 0),
            coverageStartMs = at(5, 0),
            zone = utc,
        )
        assertEquals(30.0, grid[0][21], 0.001)
        assertEquals(30.0, grid[0][22], 0.001)
        assertEquals(0.0, grid[0][23], 0.001)
    }

    @Test
    fun heatmapSplitsSessionAtMidnight() {
        val grid = PatternsMath.heatmapAverages(
            spans = listOf(Span(at(5, 23, 30), at(6, 0, 30))),
            windowStartMs = at(5, 0),
            windowEndMs = at(7, 0),
            coverageStartMs = at(5, 0),
            zone = utc,
        )
        assertEquals(30.0, grid[0][23], 0.001)
        assertEquals(30.0, grid[1][0], 0.001)
    }

    @Test
    fun heatmapAveragesOverDaysOfThatWeekday() {
        // Two Mondays in the window; use only happens on the first one, so the average is halved.
        val grid = PatternsMath.heatmapAverages(
            spans = listOf(Span(at(5, 22), at(5, 23))),
            windowStartMs = at(5, 0),
            windowEndMs = at(19, 0),
            coverageStartMs = at(5, 0),
            zone = utc,
        )
        assertEquals(30.0, grid[0][22], 0.001)
    }

    @Test
    fun heatmapIgnoresDaysBeforeCoverageStarted() {
        // Only one Monday (the 12th) falls after data collection began, so that day is not diluted.
        val grid = PatternsMath.heatmapAverages(
            spans = listOf(Span(at(12, 22), at(12, 23))),
            windowStartMs = at(5, 0),
            windowEndMs = at(19, 0),
            coverageStartMs = at(12, 0),
            zone = utc,
        )
        assertEquals(60.0, grid[0][22], 0.001)
    }

    @Test
    fun peakBlockReportsWeekdaysOnly() {
        val grid = emptyGrid()
        for (w in 0..4) grid[w][22] = 40.0
        val peak = PatternsMath.peakBlock(grid)
        assertEquals(PatternsMath.PeakBlock(PeakGroup.WEEKDAYS, 22, 23), peak)
    }

    @Test
    fun peakBlockReportsWeekendsOnly() {
        val grid = emptyGrid()
        for (w in 5..6) grid[w][10] = 40.0
        val peak = PatternsMath.peakBlock(grid)
        assertEquals(PatternsMath.PeakBlock(PeakGroup.WEEKENDS, 10, 11), peak)
    }

    @Test
    fun peakBlockReportsEveryDayWhenBothSidesOverlap() {
        val grid = emptyGrid()
        for (w in 0..6) grid[w][22] = 40.0
        val peak = PatternsMath.peakBlock(grid)
        assertEquals(PatternsMath.PeakBlock(PeakGroup.EVERY_DAY, 22, 23), peak)
    }

    @Test
    fun peakBlockExtendsToTwoHoursWhenNeighbourIsBusy() {
        val grid = emptyGrid()
        grid[0][21] = 50.0
        grid[0][22] = 40.0
        val peak = PatternsMath.peakBlock(grid)
        assertEquals(PatternsMath.PeakBlock(PeakGroup.WEEKDAYS, 21, 23), peak)
    }

    @Test
    fun peakBlockIsNullWithoutUse() {
        assertNull(PatternsMath.peakBlock(emptyGrid()))
    }

    @Test
    fun firstPickupMeasuredFromFourAm() {
        val pickups = PatternsMath.firstPickupMinutesByDay(listOf(Span(at(6, 7, 42), at(6, 8))), utc)
        assertEquals(222, pickups[at(6, 0)])
    }

    @Test
    fun sessionBeforeFourAmBelongsToPreviousDay() {
        val pickups = PatternsMath.firstPickupMinutesByDay(listOf(Span(at(6, 3, 10), at(6, 3, 20))), utc)
        assertEquals(1390, pickups[at(5, 0)])
        assertNull(pickups[at(6, 0)])
    }

    @Test
    fun sessionAtFourAmIsOffsetZero() {
        val pickups = PatternsMath.firstPickupMinutesByDay(listOf(Span(at(6, 4), at(6, 4, 5))), utc)
        assertEquals(0, pickups[at(6, 0)])
    }

    @Test
    fun firstPickupKeepsEarliestSessionOfDay() {
        val pickups = PatternsMath.firstPickupMinutesByDay(
            listOf(Span(at(6, 9), at(6, 9, 5)), Span(at(6, 7), at(6, 7, 5))),
            utc,
        )
        assertEquals(180, pickups[at(6, 0)])
    }

    @Test
    fun zeroLengthSpansAreIgnored() {
        val pickups = PatternsMath.firstPickupMinutesByDay(listOf(Span(at(6, 7), at(6, 7))), utc)
        assertTrue(pickups.isEmpty())
    }

    @Test
    fun averagePickupSkipsDaysWithoutData() {
        val pickups = mapOf(at(5, 0) to 222, at(6, 0) to 180)
        val days = listOf(at(4, 0), at(5, 0), at(6, 0))
        assertEquals(201.0, PatternsMath.averagePickupMinutes(pickups, days)!!, 0.001)
    }

    @Test
    fun averagePickupIsNullWithoutData() {
        assertNull(PatternsMath.averagePickupMinutes(emptyMap(), listOf(at(5, 0))))
    }

    @Test
    fun clockLabelWrapsAfterMidnight() {
        assertEquals("07:42", PatternsMath.clockLabel(222.0))
        assertEquals("04:00", PatternsMath.clockLabel(0.0))
        assertEquals("03:10", PatternsMath.clockLabel(1390.0))
    }

    @Test
    fun daysCollectedCountsDistinctDays() {
        val spans = listOf(
            Span(at(5, 8), at(5, 9)),
            Span(at(5, 12), at(5, 13)),
            Span(at(6, 8), at(6, 9)),
            Span(at(7, 8), at(7, 8)),
        )
        assertEquals(2, PatternsMath.daysCollected(spans, utc))
    }

    @Test
    fun lastDayStartsAreOldestFirstAndEndOnToday() {
        val now = at(11, 15)
        val days = PatternsMath.lastDayStarts(now, 3, utc)
        assertEquals(listOf(at(9, 0), at(10, 0), at(11, 0)), days)
    }
}
