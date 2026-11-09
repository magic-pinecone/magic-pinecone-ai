package org.mpc.domain.model

import kotlinx.datetime.LocalDate

/** The class days of a semester, both ends inclusive. */
data class AcademicTerm(
    val semester: String,
    val firstClassDay: LocalDate,
    val lastClassDay: LocalDate,
)
