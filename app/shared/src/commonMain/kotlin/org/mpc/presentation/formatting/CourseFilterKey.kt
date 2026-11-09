package org.mpc.presentation.formatting

import org.mpc.domain.model.CourseFilter

/**
 * A string identity for [CourseFilter], equal exactly when the filters are equal.
 * Android lazy lists save item keys in a Bundle, which cannot hold the filter itself.
 */
val CourseFilter.stableKey: String
    get() = when (this) {
        is CourseFilter.Teacher -> "teacher:$name"

        is CourseFilter.Department -> "department:$name"

        is CourseFilter.College -> "college:$name"

        is CourseFilter.Type -> "type:${type.name}"

        is CourseFilter.Credits -> "credits:$credits"

        is CourseFilter.Seats -> "seats:${availability.name}"

        is CourseFilter.Schedule -> {
            // Set order follows insertion, so equal schedules must sort before joining.
            val slotKeys = slots.sortedWith(compareBy({ it.day.order }, { it.period.order }))
                .joinToString(",") { "${it.day.name}-${it.period.name}" }
            "schedule:${match.name}:$slotKeys"
        }
    }
