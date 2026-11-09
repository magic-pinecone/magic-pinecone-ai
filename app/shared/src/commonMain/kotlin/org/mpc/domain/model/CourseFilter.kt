package org.mpc.domain.model

sealed interface CourseFilter {
    data class Teacher(
        val name: String,
    ) : CourseFilter

    data class Department(
        val name: String,
    ) : CourseFilter

    data class College(
        val name: String,
    ) : CourseFilter

    data class Type(
        val type: CourseType,
    ) : CourseFilter

    data class Credits(
        val credits: Double,
    ) : CourseFilter

    data class Seats(
        val availability: SeatAvailability,
    ) : CourseFilter

    data class Schedule(
        val slots: Set<CourseTime>,
        val match: SlotMatch,
    ) : CourseFilter
}

enum class SeatAvailability { OPEN, FULL }

enum class SlotMatch { FITS_WITHIN, OVERLAPS }
