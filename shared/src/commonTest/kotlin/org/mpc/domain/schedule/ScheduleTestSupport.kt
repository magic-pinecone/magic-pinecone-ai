package org.mpc.domain.schedule

import org.mpc.domain.model.CourseDay
import org.mpc.domain.model.CoursePeriod
import org.mpc.domain.model.CoursePlan
import org.mpc.domain.model.CourseSerialNo
import org.mpc.domain.model.CourseSummary
import org.mpc.domain.model.CourseTime
import org.mpc.domain.model.CourseType
import org.mpc.domain.model.PasswordCardType

fun scheduleCourse(
    serialNo: String,
    title: String,
    vararg classTimes: CourseTime,
) = CourseSummary(
    serialNo = CourseSerialNo(serialNo),
    classNo = "01",
    title = title,
    credit = 3.0,
    passwordCard = PasswordCardType.NONE,
    teachers = emptyList(),
    classTimes = classTimes.toList(),
    limitCnt = 50,
    admitCnt = 0,
    waitCnt = 0,
    collegeName = "理學院",
    departmentName = "數學系",
    courseType = CourseType.REQUIRED,
    detailUrl = "",
)

fun schedulePlan(vararg courses: CourseSummary) = CoursePlan("115-1", courses.associateBy { it.serialNo })

fun at(
    day: CourseDay,
    vararg periods: CoursePeriod,
) = periods.map { CourseTime(day, it) }.toTypedArray()

fun clock(
    hour: Int,
    minute: Int = 0,
) = hour * 60 + minute
