package org.mpc.data.dto

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/*
{
  "last_updated": "2026-07-12T09:06:54.266949+00:00",
  "academic_year": "115",
  "semester": "1",
  "courses": []
}
 */

@Serializable
internal data class CourseResultDto(
    val lastUpdated: Instant,
    val academicYear: String,
    val semester: String,
    val courses: List<CourseDto>,
)
