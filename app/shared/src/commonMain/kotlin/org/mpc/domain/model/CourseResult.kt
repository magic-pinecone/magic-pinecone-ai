package org.mpc.domain.model

import kotlin.time.Instant

data class CourseResult(
    val lastUpdated: Instant,
    val semester: String,
    val courses: List<CourseSummary>,
)

data class CourseDetail(
    val serialNo: String,
    val objectives: String,
    val content: String,
    val books: String,
    val teachingMethod: String,
    val gradingPolicy: String,
    val distributionConditions: List<DistributionCondition>,
)

data class DistributionCondition(
    val priority: Int,
    val rule: String,
)
