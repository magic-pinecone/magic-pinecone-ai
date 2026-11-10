package org.mpc.presentation.formatting

import org.mpc.domain.schedule.ClassMeeting
import org.mpc.domain.schedule.UpcomingMeeting

fun ClassMeeting.periodLabel(): String = periodDesc().localized()

fun UpcomingMeeting.whenLabel(): String = whenDesc().localized()
