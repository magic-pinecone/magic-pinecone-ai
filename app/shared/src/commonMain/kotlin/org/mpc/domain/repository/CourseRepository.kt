package org.mpc.domain.repository

import kotlinx.coroutines.flow.Flow
import org.mpc.domain.model.CourseDetail
import org.mpc.domain.model.CourseQuery
import org.mpc.domain.model.CourseResult
import org.mpc.domain.model.CourseSerialNo

interface CourseRepository {
    suspend fun fetchAllCourses(semester: String): CourseResult

    suspend fun searchCourses(query: CourseQuery): CourseResult

    suspend fun fetchCoursesBySerialNo(
        semester: String,
        serialNos: List<CourseSerialNo>,
    ): CourseResult

    suspend fun fetchCourseDetail(
        semester: String,
        serialNo: String,
    ): CourseDetail

    fun observeCatalog(semester: String): Flow<CourseResult?>

    // the only function that should interact with the data source
    // everything else should interact with room
    suspend fun refreshCatalog(semester: String): CourseResult
}
