package org.mpc.domain.schedule

import org.mpc.domain.model.CoursePeriod

private const val FIRST_PERIOD_START_HOUR = 8
private const val PERIOD_MINUTES = 50
private const val MINUTES_PER_HOUR = 60

// NCU course-time guide (https://pdc.adm.ncu.edu.tw/Course/course/COUR_U.pdf): period 1 starts at 08:00,
// every later period (Z included) starts on the next hour and lasts 50 minutes.
val CoursePeriod.startMinute: Int get() = (FIRST_PERIOD_START_HOUR + order - 1) * MINUTES_PER_HOUR

val CoursePeriod.endMinute: Int get() = startMinute + PERIOD_MINUTES
