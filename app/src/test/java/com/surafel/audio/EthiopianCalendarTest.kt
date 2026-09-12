package com.surafel.audio

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

class EthiopianCalendarTest {
    @Test fun suppliedReferenceDatesMatch() {
        assertEquals(EthiopianDate(2018, 13, 4), EthiopianCalendar.today(instant(2026, 9, 9, 12)))
        assertEquals(EthiopianDate(2019, 1, 1), EthiopianCalendar.today(instant(2026, 9, 11, 12)))
        assertEquals(EthiopianDate(2019, 2, 1), EthiopianCalendar.today(instant(2026, 10, 11, 12)))
        assertEquals(EthiopianDate(2019, 3, 1), EthiopianCalendar.today(instant(2026, 11, 10, 12)))
        assertEquals(5, EthiopianCalendar.weekday(EthiopianDate(2019, 1, 1)))
    }
    @Test fun pagumenAndLeapYearTransitions() {
        assertEquals(5, EthiopianCalendar.daysInMonth(2018, 13))
        assertEquals(6, EthiopianCalendar.daysInMonth(2015, 13))
        assertEquals(EthiopianDate(2015, 13, 6), EthiopianCalendar.today(instant(2023, 9, 11, 12)))
        assertEquals(EthiopianDate(2016, 1, 1), EthiopianCalendar.today(instant(2023, 9, 12, 12)))
        assertEquals(EthiopianDate(2019, 1, 1), EthiopianCalendar.adjacentMonth(EthiopianDate(2018, 13, 1), 1))
        assertEquals(EthiopianDate(2018, 13, 1), EthiopianCalendar.adjacentMonth(EthiopianDate(2019, 1, 1), -1))
    }
    @Test fun dayChangesAtAddisMidnightNotSixAmOrSixPm() {
        val midnightUtc = instant(2026, 9, 10, 21)
        assertEquals(EthiopianDate(2018, 13, 5), EthiopianCalendar.today(midnightUtc - 1))
        assertEquals(EthiopianDate(2019, 1, 1), EthiopianCalendar.today(midnightUtc))
        // 06:00 and 18:00 local on the same day do not change the calendar date.
        assertEquals(EthiopianCalendar.today(instant(2026, 9, 11, 3) - 1), EthiopianCalendar.today(instant(2026, 9, 11, 3)))
        assertEquals(EthiopianCalendar.today(instant(2026, 9, 11, 15) - 1), EthiopianCalendar.today(instant(2026, 9, 11, 15)))
        assertEquals(midnightUtc, EthiopianCalendar.nextMidnight(midnightUtc - 1))
        assertEquals(midnightUtc + EthiopianCalendar.DAY_MILLIS, EthiopianCalendar.nextMidnight(midnightUtc))
    }
    @Test fun deviceTimezoneDoesNotChangeEthiopianToday() {
        val original = TimeZone.getDefault()
        try {
            val now = instant(2026, 9, 10, 21)
            for (id in listOf("America/Los_Angeles", "Asia/Tokyo", "UTC")) {
                TimeZone.setDefault(TimeZone.getTimeZone(id))
                assertEquals(EthiopianDate(2019, 1, 1), EthiopianCalendar.today(now))
            }
        } finally { TimeZone.setDefault(original) }
    }
    @Test fun everyDateRoundTripsAcrossLeapCyclesAndCenturyBoundary() {
        for (year in 1990..2105) for (month in 1..13) for (day in 1..EthiopianCalendar.daysInMonth(year, month)) {
            val date = EthiopianDate(year, month, day)
            assertEquals(date, EthiopianCalendar.fromEpochDay(EthiopianCalendar.epochDay(date)))
        }
    }
    @Test fun invalidPagumenDayIsRejectedAndNavigationClamps() {
        assertThrows(IllegalArgumentException::class.java) { EthiopianDate(2018, 13, 6) }
        assertEquals(EthiopianDate(2018, 13, 5), EthiopianCalendar.adjacentMonth(EthiopianDate(2018, 12, 30), 1))
        assertEquals(EthiopianDate(1, 1, 1), EthiopianCalendar.adjacentMonth(EthiopianDate(1, 1, 1), -1))
        assertEquals(EthiopianDate(9999, 13, 1), EthiopianCalendar.adjacentMonth(EthiopianDate(9999, 13, 1), 1))
    }
    private fun instant(year: Int, month: Int, day: Int, hour: Int) = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
        clear(); set(year, month - 1, day, hour, 0, 0)
    }.timeInMillis
}
