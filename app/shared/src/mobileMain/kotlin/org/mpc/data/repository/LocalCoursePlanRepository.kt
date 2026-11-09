package org.mpc.data.repository

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import org.mpc.data.local.dao.CoursePlanDao
import org.mpc.data.mapper.entities.toCoursePlanEntity
import org.mpc.data.mapper.entities.toCoursePlanItemsEntity
import org.mpc.domain.model.CoursePlan
import org.mpc.domain.model.CourseSerialNo
import org.mpc.domain.repository.CoursePlanRepository
import org.mpc.domain.repository.CourseRepository

@ContributesBinding(AppScope::class)
@Inject
class LocalCoursePlanRepository(
    private val courseRepository: CourseRepository,
    private val coursePlanDao: CoursePlanDao,
) : CoursePlanRepository {
    override suspend fun loadPlan(semester: String): CoursePlan {
        val selectedCourses = coursePlanDao.findSelectedSerialNumbers(semester).map { CourseSerialNo(it) }

        if (selectedCourses.isEmpty()) {
            return CoursePlan(
                semester = semester,
                selectedCourses = mapOf(),
            )
        }

        val courseResult = courseRepository.fetchCoursesBySerialNo(semester, selectedCourses)

        return CoursePlan(
            semester = semester,
            selectedCourses = courseResult.courses.associateBy { it.serialNo },
        )
    }

    override suspend fun savePlan(plan: CoursePlan) {
        coursePlanDao.replaceItems(plan.toCoursePlanEntity(), plan.toCoursePlanItemsEntity())
    }

    override suspend fun deletePlan(semester: String) {
        coursePlanDao.deletePlan(semester)
    }
}
