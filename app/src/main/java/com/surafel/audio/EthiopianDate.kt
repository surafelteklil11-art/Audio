package com.surafel.audio

import java.text.SimpleDateFormat
import java.util.GregorianCalendar
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Ethiopian civil dates. The day starts at midnight in Addis Ababa, not at 06:00. */
data class EthiopianDate(val year: Int, val month: Int, val day: Int) {
    init {
        require(year in 1..9999 && month in 1..13)
        require(day in 1..EthiopianCalendar.daysInMonth(year, month))
    }
}

object EthiopianCalendar {
    val months = listOf("መስከረም", "ጥቅምት", "ኅዳር", "ታኅሣሥ", "ጥር", "የካቲት", "መጋቢት", "ሚያዝያ", "ግንቦት", "ሰኔ", "ሐምሌ", "ነሐሴ", "ጳጉሜን")
    val weekdays = listOf("እሁድ", "ሰኞ", "ማክሰኞ", "ረቡዕ", "ሐሙስ", "ዓርብ", "ቅዳሜ")
    const val DAY_MILLIS = 86_400_000L
    private val addis get() = TimeZone.getTimeZone("Africa/Addis_Ababa")
    private val utc get() = TimeZone.getTimeZone("UTC")
    // 1 Meskerem 2019 = 11 September 2026. Serial arithmetic handles every
    // four-year Ethiopian leap cycle, including Gregorian century differences.
    private val anchorDay = GregorianCalendar(utc).apply {
        clear(); set(2026, Calendar.SEPTEMBER, 11)
    }.timeInMillis / DAY_MILLIS
    private val anchorSerial = serial(EthiopianDate(2019, 1, 1))

    fun isLeapYear(year: Int) = year % 4 == 3
    fun daysInMonth(year: Int, month: Int): Int {
        require(year in 1..9999 && month in 1..13)
        return if (month < 13) 30 else if (isLeapYear(year)) 6 else 5
    }
    private fun serial(date: EthiopianDate): Long = 365L * (date.year - 1) + date.year / 4 + 30L * (date.month - 1) + date.day - 1
    fun epochDay(date: EthiopianDate): Long = anchorDay + serial(date) - anchorSerial
    fun fromEpochDay(day: Long): EthiopianDate {
        val value = day - anchorDay + anchorSerial
        var year = Math.floorDiv(value * 4 + 1463, 1461).toInt().coerceIn(1, 9999)
        while (year > 1 && serial(EthiopianDate(year, 1, 1)) > value) year--
        while (year < 9999 && serial(EthiopianDate(year + 1, 1, 1)) <= value) year++
        val inYear = (value - serial(EthiopianDate(year, 1, 1))).toInt()
        return EthiopianDate(year, inYear / 30 + 1, inYear % 30 + 1)
    }
    fun today(nowMillis: Long = System.currentTimeMillis()): EthiopianDate =
        fromEpochDay(Math.floorDiv(nowMillis + addis.getOffset(nowMillis), DAY_MILLIS))
    fun nextMidnight(nowMillis: Long): Long = GregorianCalendar(addis).apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis
    fun weekday(date: EthiopianDate): Int = Math.floorMod(epochDay(date) + 4, 7).toInt()
    fun gregorian(date: EthiopianDate): String = SimpleDateFormat("EEE, dd MMM yyyy", Locale.ENGLISH).apply {
        timeZone = utc
    }.format(java.util.Date(epochDay(date) * DAY_MILLIS))
    fun label(date: EthiopianDate) = "${months[date.month - 1]} ${date.day}፣ ${date.year} ዓ.ም."
    fun adjacentMonth(date: EthiopianDate, delta: Int): EthiopianDate {
        val index = ((date.year - 1) * 13 + date.month - 1 + delta).coerceIn(0, 9999 * 13 - 1)
        val year = index / 13 + 1; val month = index % 13 + 1
        return EthiopianDate(year, month, date.day.coerceAtMost(daysInMonth(year, month)))
    }
}
