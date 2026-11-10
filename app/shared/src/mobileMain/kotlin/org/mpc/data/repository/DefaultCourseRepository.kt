package org.mpc.data.repository

import androidx.compose.ui.text.intl.Locale
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.mpc.data.dataSource.CourseDataSource
import org.mpc.data.local.dao.CourseCatalogDao
import org.mpc.data.local.entities.toDomain
import org.mpc.data.local.entities.toLocalCatalogEntities
import org.mpc.data.mapper.dto.toDomain
import org.mpc.domain.model.CourseDetail
import org.mpc.domain.model.CourseQuery
import org.mpc.domain.model.CourseResult
import org.mpc.domain.model.CourseSerialNo
import org.mpc.domain.repository.CourseRepository
import org.mpc.domain.search.searchCourses

// TODO: Add fetched-at freshness policy.
@ContributesBinding(AppScope::class)
@Inject
internal class DefaultCourseRepository(
    private val courseDataSource: CourseDataSource,
    private val courseCatalogDao: CourseCatalogDao,
    private val currentLanguage: () -> String = { Locale.current.toLanguageTag() },
) : CourseRepository {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun fetchAllCourses(semester: String): CourseResult {
        val language = currentLanguage()
        val cached = courseCatalogDao.findCatalogEntities(semester)
        if (cached?.catalog?.language == language) return cached.toDomain()

        return try {
            refreshCatalog(semester, language)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // An offline user keeps the catalog in the language it was fetched in.
            cached?.toDomain() ?: throw e
        }
    }

    override suspend fun searchCourses(query: CourseQuery): CourseResult {
        val result = fetchAllCourses(query.semester)
        return result.copy(courses = searchCourses(result.courses, query))
    }

    override suspend fun fetchCoursesBySerialNo(
        semester: String,
        serialNos: List<CourseSerialNo>,
    ): CourseResult {
        val result = fetchAllCourses(semester)

        val serialNoSet = serialNos.toSet()

        val filteredCourse =
            result.courses.filter { course ->
                course.serialNo in serialNoSet
            }

        return result.copy(courses = filteredCourse)
    }

    override suspend fun fetchCourseDetail(
        semester: String,
        serialNo: String,
    ): CourseDetail = courseDataSource.getCourseDetails(semester, serialNo).toDomain()

    override fun observeCatalog(semester: String): Flow<CourseResult?> = courseCatalogDao.observeCatalogMetadata(semester).map {
        courseCatalogDao.findCatalogEntities(semester)?.toDomain()
    }

    override suspend fun refreshCatalog(semester: String): CourseResult = refreshCatalog(semester, currentLanguage())

    private suspend fun refreshCatalog(semester: String, language: String): CourseResult {
        val remoteResult = courseDataSource.getAllCourses(semester).toDomain()
        require(remoteResult.semester == semester) {
            "Remote catalog semester ${remoteResult.semester} does not match requested semester $semester"
        }

        courseCatalogDao.saveCatalog(remoteResult.toLocalCatalogEntities(language))

        return requireNotNull(courseCatalogDao.findCatalogEntities(semester)?.toDomain()) {
            "Catalog $semester was not available after saving"
        }
    }
}
