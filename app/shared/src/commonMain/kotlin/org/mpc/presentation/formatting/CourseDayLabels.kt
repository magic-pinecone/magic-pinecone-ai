package org.mpc.presentation.formatting

import dev.icerock.moko.resources.StringResource
import org.mpc.domain.model.CourseDay
import org.mpc.shared.MR
import org.mpc.shared.catalog_day_friday
import org.mpc.shared.catalog_day_monday
import org.mpc.shared.catalog_day_saturday
import org.mpc.shared.catalog_day_sunday
import org.mpc.shared.catalog_day_thursday
import org.mpc.shared.catalog_day_tuesday
import org.mpc.shared.catalog_day_unknown
import org.mpc.shared.catalog_day_wednesday

fun CourseDay.shortName(): StringResource = when (this) {
    CourseDay.MONDAY -> MR.strings.catalog_day_monday
    CourseDay.TUESDAY -> MR.strings.catalog_day_tuesday
    CourseDay.WEDNESDAY -> MR.strings.catalog_day_wednesday
    CourseDay.THURSDAY -> MR.strings.catalog_day_thursday
    CourseDay.FRIDAY -> MR.strings.catalog_day_friday
    CourseDay.SATURDAY -> MR.strings.catalog_day_saturday
    CourseDay.SUNDAY -> MR.strings.catalog_day_sunday
    CourseDay.UNKNOWN -> MR.strings.catalog_day_unknown
}
