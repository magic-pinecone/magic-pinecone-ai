package org.mpc.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.mpc.data.local.database.CourseCatalogEntity
import org.mpc.data.local.database.CourseEntity
import org.mpc.data.local.database.CourseTeacherEntity
import org.mpc.data.local.database.CourseTimeEntity
import org.mpc.data.local.entities.LocalCatalogEntities

@Dao
interface CourseCatalogDao {
    @Query("SELECT * FROM course_catalog WHERE semester = :semester")
    fun observeCatalogMetadata(semester: String): Flow<CourseCatalogEntity?>

    @Upsert
    suspend fun upsertCatalog(entity: CourseCatalogEntity)

    @Upsert
    suspend fun upsertCourses(entities: List<CourseEntity>)

    @Upsert
    suspend fun upsertCourseTimes(entities: List<CourseTimeEntity>)

    @Upsert
    suspend fun upsertCourseTeachers(entities: List<CourseTeacherEntity>)

    @Query("SELECT * FROM course_catalog WHERE semester = :semester")
    suspend fun findCatalog(semester: String): CourseCatalogEntity?

    @Query("DELETE FROM course_catalog WHERE semester = :semester")
    suspend fun deleteCatalog(semester: String)

    @Query(
        """
        DELETE FROM course
        WHERE semester = :semester
          AND serialNo IN (:serialNos)
    """,
    )
    suspend fun deleteCourses(semester: String, serialNos: List<String>)

    @Query("SELECT * FROM course WHERE semester = :semester ORDER BY serialNo")
    suspend fun findCourses(semester: String): List<CourseEntity>

    @Query("SELECT * FROM course_time WHERE semester = :semester")
    suspend fun findCourseTimes(semester: String): List<CourseTimeEntity>

    @Query("SELECT * FROM course_teacher WHERE semester = :semester")
    suspend fun findCourseTeachers(semester: String): List<CourseTeacherEntity>

    @Query("DELETE FROM course_time WHERE semester = :semester")
    suspend fun deleteCourseTimes(semester: String)

    @Query("DELETE FROM course_teacher WHERE semester = :semester")
    suspend fun deleteCourseTeachers(semester: String)

    @Transaction
    suspend fun saveCatalog(entities: LocalCatalogEntities) {
        val incomingCourseSerialNos = entities.courses
            .mapTo(mutableSetOf()) { it.serialNo }
        val removedCourseSerialNos = findCourses(entities.catalog.semester)
            .map { it.serialNo }
            .filterNot { it in incomingCourseSerialNos }

        if (removedCourseSerialNos.isNotEmpty()) {
            deleteCourses(
                semester = entities.catalog.semester,
                serialNos = removedCourseSerialNos,
            )
        }

        upsertCatalog(entities.catalog)
        upsertCourses(entities.courses)

        deleteCourseTimes(entities.catalog.semester)
        if (entities.courseTimes.isNotEmpty()) {
            upsertCourseTimes(entities.courseTimes)
        }

        deleteCourseTeachers(entities.catalog.semester)
        if (entities.teachers.isNotEmpty()) {
            upsertCourseTeachers(entities.teachers)
        }
    }

    @Transaction
    suspend fun findCatalogEntities(semester: String): LocalCatalogEntities? {
        val catalog = findCatalog(semester) ?: return null

        return LocalCatalogEntities(
            catalog = catalog,
            courses = findCourses(semester),
            teachers = findCourseTeachers(semester),
            courseTimes = findCourseTimes(semester),
        )
    }
}
