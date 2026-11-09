package org.mpc.domain.model

import kotlin.jvm.JvmInline

/** A course the student is enrolled in on ee-class, as its dashboard lists it. */
data class EeclassCourse(
    val number: EeclassCourseNumber,
    val name: String,
)

/** ee-class's own number for a course, from `/course/{number}`. Unrelated to a Course Identity. */
@JvmInline
value class EeclassCourseNumber(
    val value: String,
) {
    init {
        require(value.matches(Regex("""\d+""")))
    }
}
