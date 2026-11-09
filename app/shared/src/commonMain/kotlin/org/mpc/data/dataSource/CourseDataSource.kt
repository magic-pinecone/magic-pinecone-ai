package org.mpc.data.dataSource

import org.mpc.data.dto.CourseDetailDto
import org.mpc.data.dto.CourseResultDto

internal interface CourseDataSource {
    suspend fun getAllCourses(semester: String): CourseResultDto

    suspend fun getCourseDetails(
        semester: String,
        serialNo: String,
    ): CourseDetailDto
}
