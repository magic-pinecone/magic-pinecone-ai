package org.mpc.domain.model

import org.mpc.domain.session.SitePath
import kotlin.jvm.JvmInline

/** The first page of an ee-class Course's announcement, material, and homework lists. */
data class EeclassCourseContent(
    val bulletins: List<EeclassBulletin>,
    val materials: List<EeclassMaterial>,
    val homework: List<EeclassHomework>,
)

// Dates and times stay as ee-class displays them: some omit the year, and materials show relative times.

/** An announcement. ee-class opens announcements in a dialog on the course's list, so it has no page of its own. */
data class EeclassBulletin(
    val number: EeclassBulletinNumber,
    val title: String,
    val author: String,
    val posted: String,
)

/** ee-class's number for an announcement, from its dialog link. */
@JvmInline
value class EeclassBulletinNumber(
    val value: String,
) {
    init {
        require(value.matches(Regex("""\d+""")))
    }
}

/** An announcement's dialog: [info] is ee-class's summary line, and [bodyHtml] is the announcement as the teacher wrote it. */
data class EeclassBulletinDetail(
    val info: String,
    val bodyHtml: String,
    val attachments: List<EeclassAttachment>,
)

data class EeclassAttachment(
    val name: String,
    val path: SitePath,
)

data class EeclassMaterial(
    val title: String,
    val author: String,
    val updated: String,
    val path: SitePath,
)

data class EeclassHomework(
    val title: String,
    val opens: String,
    val due: String,
    val score: String,
    val path: SitePath,
)
