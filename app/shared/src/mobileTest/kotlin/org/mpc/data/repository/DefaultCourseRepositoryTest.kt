package org.mpc.data.repository

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.mpc.data.dataSource.CourseDataSource
import org.mpc.data.dto.CourseDetailDto
import org.mpc.data.dto.CourseDto
import org.mpc.data.dto.CourseResultDto
import org.mpc.data.dto.CourseTypeDto
import org.mpc.data.dto.PasswordCardTypeDto
import org.mpc.data.local.dao.CourseCatalogDao
import org.mpc.data.local.database.CourseCatalogEntity
import org.mpc.data.local.database.CourseEntity
import org.mpc.data.local.database.CourseTeacherEntity
import org.mpc.data.local.database.CourseTimeEntity
import org.mpc.data.local.entities.LocalCatalogEntities
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

private const val SEMESTER = "115-1"

@Suppress("TooManyFunctions")
private class FakeCourseCatalogDao : CourseCatalogDao {
    var stored: LocalCatalogEntities? = null

    override suspend fun saveCatalog(entities: LocalCatalogEntities) {
        stored = entities
    }

    override suspend fun findCatalogEntities(semester: String): LocalCatalogEntities? = stored?.takeIf { it.catalog.semester == semester }

    override fun observeCatalogMetadata(semester: String): Flow<CourseCatalogEntity?> = error("unused")

    override suspend fun upsertCatalog(entity: CourseCatalogEntity) = error("unused")

    override suspend fun upsertCourses(entities: List<CourseEntity>) = error("unused")

    override suspend fun upsertCourseTimes(entities: List<CourseTimeEntity>) = error("unused")

    override suspend fun upsertCourseTeachers(entities: List<CourseTeacherEntity>) = error("unused")

    override suspend fun findCatalog(semester: String): CourseCatalogEntity? = error("unused")

    override suspend fun deleteCatalog(semester: String) = error("unused")

    override suspend fun deleteCourses(semester: String, serialNos: List<String>) = error("unused")

    override suspend fun findCourses(semester: String): List<CourseEntity> = error("unused")

    override suspend fun findCourseTimes(semester: String): List<CourseTimeEntity> = error("unused")

    override suspend fun findCourseTeachers(semester: String): List<CourseTeacherEntity> = error("unused")

    override suspend fun deleteCourseTimes(semester: String) = error("unused")

    override suspend fun deleteCourseTeachers(semester: String) = error("unused")
}

private class FakeCourseDataSource(private val titleFor: () -> String) : CourseDataSource {
    var calls = 0
    var failure: Exception? = null

    override suspend fun getAllCourses(semester: String): CourseResultDto {
        calls++
        failure?.let { throw it }
        return CourseResultDto(
            lastUpdated = Instant.fromEpochMilliseconds(0),
            academicYear = "115",
            semester = "1",
            courses = listOf(courseDto(titleFor())),
        )
    }

    override suspend fun getCourseDetails(semester: String, serialNo: String): CourseDetailDto = error("unused")
}

private fun courseDto(title: String) = CourseDto(
    serialNo = "01001",
    classNo = "PE1011-A",
    title = title,
    credit = 0.0,
    passwordCard = PasswordCardTypeDto.OPTIONAL,
    teachers = emptyList(),
    classTimes = emptyList(),
    limitCnt = 0,
    admitCnt = 0,
    waitCnt = 0,
    collegeName = "college",
    departmentName = "department",
    courseType = CourseTypeDto.REQUIRED,
    detailUrl = "https://example.test/01001",
)

private class Harness(initialLanguage: String) {
    var language = initialLanguage
    val dao = FakeCourseCatalogDao()
    val dataSource = FakeCourseDataSource { if (language == "en") "PE" else "體育" }
    val repository = DefaultCourseRepository(dataSource, dao) { language }
}

val defaultCourseRepositoryTests by testSuite {
    test("a catalog cached in the current language is served without a remote call") {
        runTest {
            val harness = Harness("zh-TW")
            harness.repository.fetchAllCourses(SEMESTER)
            assertEquals(1, harness.dataSource.calls)

            val again = harness.repository.fetchAllCourses(SEMESTER)

            assertEquals(1, harness.dataSource.calls)
            assertEquals("體育", again.courses.single().title)
        }
    }

    test("a catalog cached in another language is refreshed and remembers the new language") {
        runTest {
            val harness = Harness("zh-TW")
            harness.repository.fetchAllCourses(SEMESTER)

            harness.language = "en"
            val result = harness.repository.fetchAllCourses(SEMESTER)

            assertEquals(2, harness.dataSource.calls)
            assertEquals("PE", result.courses.single().title)
            assertEquals("en", harness.dao.stored?.catalog?.language)
            harness.repository.fetchAllCourses(SEMESTER)
            assertEquals(2, harness.dataSource.calls)
        }
    }

    test("a failed refresh falls back to the catalog cached in the old language") {
        runTest {
            val harness = Harness("zh-TW")
            harness.repository.fetchAllCourses(SEMESTER)

            harness.language = "en"
            harness.dataSource.failure = IOException("offline")
            val result = harness.repository.fetchAllCourses(SEMESTER)

            assertEquals("體育", result.courses.single().title)
            assertEquals("zh-TW", harness.dao.stored?.catalog?.language)
        }
    }

    test("a failed refresh with nothing cached still fails") {
        runTest {
            val harness = Harness("en")
            harness.dataSource.failure = IOException("offline")

            assertFailsWith<IOException> { harness.repository.fetchAllCourses(SEMESTER) }
        }
    }
}
