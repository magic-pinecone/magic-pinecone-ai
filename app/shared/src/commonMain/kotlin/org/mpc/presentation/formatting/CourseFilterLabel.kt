package org.mpc.presentation.formatting

import dev.icerock.moko.resources.StringResource
import dev.icerock.moko.resources.desc.PluralFormatted
import dev.icerock.moko.resources.desc.Resource
import dev.icerock.moko.resources.desc.ResourceFormatted
import dev.icerock.moko.resources.desc.StringDesc
import org.mpc.domain.model.CourseFilter
import org.mpc.domain.model.CourseType
import org.mpc.domain.model.SeatAvailability
import org.mpc.shared.MR
import org.mpc.shared.catalog_label_college
import org.mpc.shared.catalog_label_credits
import org.mpc.shared.catalog_label_department
import org.mpc.shared.catalog_label_schedule
import org.mpc.shared.catalog_label_teacher
import org.mpc.shared.catalog_seats_full
import org.mpc.shared.catalog_seats_open
import org.mpc.shared.catalog_type_elective
import org.mpc.shared.catalog_type_required
import org.mpc.shared.catalog_type_unknown

/** Chinese-only text for the App Intents, which have not moved to a string catalog yet; Compose and SwiftUI use [labelDesc]. */
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

fun CourseFilter.labelDesc(): StringDesc = when (this) {
    is CourseFilter.Teacher -> StringDesc.ResourceFormatted(MR.strings.catalog_label_teacher, name)

    is CourseFilter.Department -> StringDesc.ResourceFormatted(MR.strings.catalog_label_department, name)

    is CourseFilter.College -> StringDesc.ResourceFormatted(MR.strings.catalog_label_college, name)

    is CourseFilter.Type -> StringDesc.Resource(type.labelResource())

    is CourseFilter.Credits -> StringDesc.ResourceFormatted(MR.strings.catalog_label_credits, credits.creditLabel())

    is CourseFilter.Seats -> StringDesc.Resource(
        when (availability) {
            SeatAvailability.OPEN -> MR.strings.catalog_seats_open
            SeatAvailability.FULL -> MR.strings.catalog_seats_full
        },
    )

    is CourseFilter.Schedule -> StringDesc.PluralFormatted(MR.plurals.catalog_label_schedule, slots.size, slots.size)
}

fun CourseType.labelResource(): StringResource = when (this) {
    CourseType.REQUIRED -> MR.strings.catalog_type_required
    CourseType.ELECTIVE -> MR.strings.catalog_type_elective
    CourseType.UNKNOWN -> MR.strings.catalog_type_unknown
}
