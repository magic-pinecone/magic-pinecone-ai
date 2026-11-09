package org.mpc.presentation.formatting

import org.mpc.domain.model.CourseFilter
import org.mpc.domain.model.SeatAvailability

fun CourseFilter.label(): String = when (this) {
    is CourseFilter.Teacher -> "老師：$name"

    is CourseFilter.Department -> "系所：$name"

    is CourseFilter.College -> "學院：$name"

    is CourseFilter.Type -> type.description

    is CourseFilter.Credits -> "${credits.creditLabel()} 學分"

    is CourseFilter.Seats -> when (availability) {
        SeatAvailability.OPEN -> "尚有名額"
        SeatAvailability.FULL -> "已額滿"
    }

    is CourseFilter.Schedule -> "時段：${slots.size} 節"
}
