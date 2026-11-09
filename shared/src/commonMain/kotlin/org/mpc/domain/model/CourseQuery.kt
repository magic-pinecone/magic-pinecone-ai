package org.mpc.domain.model

data class CourseQuery(
    val semester: String,
    val keyword: String = "",
    val filters: Set<CourseFilter> = emptySet(),
)
