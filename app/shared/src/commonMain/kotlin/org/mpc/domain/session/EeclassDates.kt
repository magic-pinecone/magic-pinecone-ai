package org.mpc.domain.session

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlin.math.abs

private val DATE_TIME = Regex("""(?:(\d{4})[-/])?(\d{1,2})[-/](\d{1,2})(?:\s+(\d{1,2}):(\d{2}))?""")
private const val HOUR_GROUP = 4
private const val MINUTE_GROUP = 5

/**
 * A date as ee-class lists it ("2026-10-20", "10-21 23:59", "2025-12-30 23:59") on campus time, or null when [text]
 * is not one. A date without a year is the one nearest [today], so a December list read in January keeps its year.
 * A date without a time is taken at midnight.
 */
fun eeclassDateTime(text: String, today: LocalDate): LocalDateTime? {
    val match = DATE_TIME.matchEntire(text.trim()) ?: return null
    val (year, month, day) = match.destructured
    val hour = match.groupValues[HOUR_GROUP]
    val time = if (hour.isEmpty()) LocalTime(0, 0) else localTime(hour.toInt(), match.groupValues[MINUTE_GROUP].toInt()) ?: return null
    val date = if (year.isNotEmpty()) {
        localDate(year.toInt(), month.toInt(), day.toInt())
    } else {
        (today.year - 1..today.year + 1)
            .mapNotNull { localDate(it, month.toInt(), day.toInt()) }
            .minByOrNull { abs(today.daysUntil(it)) }
    }
    return date?.let { LocalDateTime(it, time) }
}

private fun localDate(year: Int, month: Int, day: Int): LocalDate? = runCatching { LocalDate(year, month, day) }.getOrNull()

private fun localTime(hour: Int, minute: Int): LocalTime? = runCatching { LocalTime(hour, minute) }.getOrNull()
