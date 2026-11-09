package org.mpc.calendar

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import org.mpc.di.AppGraph
import org.mpc.domain.model.CampusTimeZone
import org.mpc.domain.schedule.ClassOccurrence
import org.mpc.domain.schedule.occurrencesIn
import org.mpc.presentation.formatting.periodLabel
import kotlin.time.Duration.Companion.minutes

private const val ACCOUNT_NAME = "神奇松果"
private const val CALENDAR_COLOR = 0xFF4F7A5A.toInt()

/** Mirrors the Saved Course Plan into one local calendar the app owns, so exporting again replaces instead of duplicating. */
internal object ClassCalendar {
    const val TITLE = "神奇松果課表"
    val permissions = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    /** Replaces this term's events with the Saved Course Plan's classes; null when the term's class days are not known. */
    suspend fun exportSavedPlan(
        context: Context,
        appGraph: AppGraph,
        semester: String,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): Int? = withContext(ioDispatcher) {
        val term = appGraph.academicTermRepository.term(semester) ?: return@withContext null
        val occurrences = appGraph.coursePlanRepository.loadPlan(semester).occurrencesIn(term)
        val resolver = context.contentResolver
        val calendarId = ownedCalendarId(resolver)
        val operations = arrayListOf(
            ContentProviderOperation.newDelete(Events.CONTENT_URI)
                .withSelection(
                    "${Events.CALENDAR_ID} = ? AND ${Events.DTSTART} >= ? AND ${Events.DTSTART} < ?",
                    arrayOf(
                        calendarId.toString(),
                        term.firstClassDay.startMillis().toString(),
                        term.lastClassDay.plus(DatePeriod(days = 1)).startMillis().toString(),
                    ),
                )
                .build(),
        )
        occurrences.mapTo(operations) { occurrence ->
            ContentProviderOperation.newInsert(Events.CONTENT_URI)
                .withValues(occurrence.eventValues(calendarId))
                .build()
        }
        resolver.applyBatch(CalendarContract.AUTHORITY, operations)
        occurrences.size
    }

    fun resultMessage(classCount: Int?): String = when (classCount) {
        null -> "還沒有本學期的上課日期，暫時無法加入行事曆。"
        0 -> "已儲存的課表沒有課程，已清空這學期的「$TITLE」。"
        else -> "已將 $classCount 堂課加入「$TITLE」行事曆。"
    }

    private fun ownedCalendarId(resolver: ContentResolver): Long {
        resolver.query(
            Calendars.CONTENT_URI,
            arrayOf(Calendars._ID),
            "${Calendars.ACCOUNT_NAME} = ? AND ${Calendars.ACCOUNT_TYPE} = ?",
            arrayOf(ACCOUNT_NAME, CalendarContract.ACCOUNT_TYPE_LOCAL),
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) return cursor.getLong(0) }

        // Only a sync adapter may create a calendar, so the app acts as one for its own local account.
        val uri = Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, TITLE)
            put(Calendars.CALENDAR_DISPLAY_NAME, TITLE)
            put(Calendars.CALENDAR_COLOR, CALENDAR_COLOR)
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
            put(Calendars.CALENDAR_TIME_ZONE, CampusTimeZone.id)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
        }
        val created = checkNotNull(resolver.insert(uri, values)) { "Calendar provider refused to create $TITLE" }
        return ContentUris.parseId(created)
    }

    private fun ClassOccurrence.eventValues(calendarId: Long) = ContentValues().apply {
        val dayStart = date.startMillis()
        val course = meeting.course
        put(Events.CALENDAR_ID, calendarId)
        put(Events.TITLE, course.title)
        put(Events.DTSTART, dayStart + meeting.startMinute.minutes.inWholeMilliseconds)
        put(Events.DTEND, dayStart + meeting.endMinute.minutes.inWholeMilliseconds)
        put(Events.EVENT_TIMEZONE, CampusTimeZone.id)
        put(
            Events.DESCRIPTION,
            listOf(meeting.periodLabel(), course.teachers.joinToString("、"), "課號 ${course.serialNumber}")
                .filter { it.isNotEmpty() }
                .joinToString("\n"),
        )
    }

    private fun LocalDate.startMillis() = atStartOfDayIn(CampusTimeZone).toEpochMilliseconds()
}
