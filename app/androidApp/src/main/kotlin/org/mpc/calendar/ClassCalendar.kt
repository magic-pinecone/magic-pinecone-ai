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
import dev.icerock.moko.resources.desc.PluralFormatted
import dev.icerock.moko.resources.desc.Resource
import dev.icerock.moko.resources.desc.ResourceFormatted
import dev.icerock.moko.resources.desc.StringDesc
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
import org.mpc.presentation.formatting.periodDesc
import org.mpc.shared.MR
import org.mpc.shared.planning_calendar_course_number
import org.mpc.shared.planning_calendar_result_added
import org.mpc.shared.planning_calendar_result_cleared
import org.mpc.shared.planning_calendar_result_no_term
import org.mpc.shared.planning_calendar_title
import org.mpc.shared.planning_list_separator
import kotlin.time.Duration.Companion.minutes

private const val ACCOUNT_NAME = "神奇松果"
private const val CALENDAR_COLOR = 0xFF4F7A5A.toInt()

/** Mirrors the Saved Course Plan into one local calendar the app owns, so exporting again replaces instead of duplicating. */
internal object ClassCalendar {
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
        val title = StringDesc.Resource(MR.strings.planning_calendar_title).toString(context)
        val resolver = context.contentResolver
        val calendarId = ownedCalendarId(resolver, title)
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
                .withValues(occurrence.eventValues(context, calendarId))
                .build()
        }
        resolver.applyBatch(CalendarContract.AUTHORITY, operations)
        occurrences.size
    }

    fun resultMessage(context: Context, classCount: Int?): String {
        val title = StringDesc.Resource(MR.strings.planning_calendar_title).toString(context)
        return when (classCount) {
            null -> StringDesc.Resource(MR.strings.planning_calendar_result_no_term)
            0 -> StringDesc.ResourceFormatted(MR.strings.planning_calendar_result_cleared, title)
            else -> StringDesc.PluralFormatted(MR.plurals.planning_calendar_result_added, classCount, classCount, title)
        }.toString(context)
    }

    private fun ownedCalendarId(resolver: ContentResolver, title: String): Long {
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
            put(Calendars.NAME, title)
            put(Calendars.CALENDAR_DISPLAY_NAME, title)
            put(Calendars.CALENDAR_COLOR, CALENDAR_COLOR)
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
            put(Calendars.CALENDAR_TIME_ZONE, CampusTimeZone.id)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
        }
        val created = checkNotNull(resolver.insert(uri, values)) { "Calendar provider refused to create $title" }
        return ContentUris.parseId(created)
    }

    private fun ClassOccurrence.eventValues(context: Context, calendarId: Long) = ContentValues().apply {
        val dayStart = date.startMillis()
        val course = meeting.course
        put(Events.CALENDAR_ID, calendarId)
        put(Events.TITLE, course.title)
        put(Events.DTSTART, dayStart + meeting.startMinute.minutes.inWholeMilliseconds)
        put(Events.DTEND, dayStart + meeting.endMinute.minutes.inWholeMilliseconds)
        put(Events.EVENT_TIMEZONE, CampusTimeZone.id)
        put(
            Events.DESCRIPTION,
            listOf(
                meeting.periodDesc().toString(context),
                course.teachers.joinToString(StringDesc.Resource(MR.strings.planning_list_separator).toString(context)),
                StringDesc.ResourceFormatted(MR.strings.planning_calendar_course_number, course.serialNumber).toString(context),
            )
                .filter { it.isNotEmpty() }
                .joinToString("\n"),
        )
    }

    private fun LocalDate.startMillis() = atStartOfDayIn(CampusTimeZone).toEpochMilliseconds()
}
