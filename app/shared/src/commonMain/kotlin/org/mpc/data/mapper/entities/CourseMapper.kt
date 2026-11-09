package org.mpc.data.mapper.entities

import org.mpc.data.local.database.CourseEntity
import org.mpc.data.local.database.CourseTimeEntity
import org.mpc.domain.model.CourseDay
import org.mpc.domain.model.CoursePeriod
import org.mpc.domain.model.CourseSerialNo
import org.mpc.domain.model.CourseSummary
import org.mpc.domain.model.CourseTime
import org.mpc.domain.model.CourseType
import org.mpc.domain.model.PasswordCardType

internal fun CourseEntity.toDomain(
    teachers: List<String>,
    classTimes: List<CourseTime>,
): CourseSummary = CourseSummary(
    serialNo = CourseSerialNo(serialNo),
    classNo = classNo,
    title = title,
    credit = credit,
    passwordCard = passwordCard.toPasswordCardType(),
    teachers = teachers,
    classTimes = classTimes,
    limitCnt = limitCnt,
    admitCnt = admitCnt,
    waitCnt = waitCnt,
    collegeName = collegeName,
    departmentName = departmentName,
    courseType = courseType.toCourseType(),
    detailUrl = detailUrl,
)

internal fun CourseSummary.toEntity(semester: String): CourseEntity = CourseEntity(
    semester = semester,
    serialNo = serialNo.value,
    classNo = classNo,
    title = title,
    credit = credit,
    passwordCard = passwordCard.name,
    limitCnt = limitCnt,
    admitCnt = admitCnt,
    waitCnt = waitCnt,
    collegeName = collegeName,
    departmentName = departmentName,
    courseType = courseType.name,
    detailUrl = detailUrl,
)

internal fun CourseTimeEntity.toDomainOrNull(): CourseTime? {
    val day = CourseDay.entries.firstOrNull { it.code == dayCode.toString() }
    val period = CoursePeriod.entries.firstOrNull { it.description == periodCode }

    return if (day != null && period != null) {
        CourseTime(day = day, period = period)
    } else {
        null
    }
}

internal fun CourseTime.toEntity(
    semester: String,
    serialNo: CourseSerialNo,
): CourseTimeEntity = CourseTimeEntity(
    semester = semester,
    serialNo = serialNo.value,
    dayCode = day.code.toInt(),
    periodCode = period.description,
)

internal fun String.toPasswordCardType(): PasswordCardType = when (this) {
    "ALL" -> PasswordCardType.ALL
    "OPTIONAL" -> PasswordCardType.OPTIONAL
    "NONE" -> PasswordCardType.NONE
    else -> error("Unknown password card type in local storage: $this")
}

internal fun String.toCourseType(): CourseType = when (this) {
    "REQUIRED" -> CourseType.REQUIRED
    "ELECTIVE" -> CourseType.ELECTIVE
    "UNKNOWN" -> CourseType.UNKNOWN
    else -> CourseType.UNKNOWN
}
