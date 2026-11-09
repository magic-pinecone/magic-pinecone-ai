package org.mpc.domain.model

data class CoursePlan(
    val semester: String,
    val selectedCourses: Map<CourseSerialNo, CourseSummary>,
)

fun CoursePlan.addCourse(course: CourseSummary): CoursePlan = copy(
    selectedCourses = selectedCourses + (course.serialNo to course),
)

fun CoursePlan.removeCourse(course: CourseSummary): CoursePlan = copy(
    selectedCourses = selectedCourses - course.serialNo,
)
