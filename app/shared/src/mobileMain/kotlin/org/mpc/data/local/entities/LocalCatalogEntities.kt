package org.mpc.data.local.entities

import org.mpc.data.local.database.CourseCatalogEntity
import org.mpc.data.local.database.CourseEntity
import org.mpc.data.local.database.CourseTeacherEntity
import org.mpc.data.local.database.CourseTimeEntity
import org.mpc.data.mapper.entities.toDomain
import org.mpc.data.mapper.entities.toDomainOrNull
import org.mpc.data.mapper.entities.toEntity
import org.mpc.domain.model.CourseResult
import kotlin.time.Instant

data class LocalCatalogEntities(
    val catalog: CourseCatalogEntity,
    val courses: List<CourseEntity>,
    val teachers: List<CourseTeacherEntity>,
    val courseTimes: List<CourseTimeEntity>,
)

internal fun CourseResult.toLocalCatalogEntities(): LocalCatalogEntities {
    val courseEntities = courses.map { it.toEntity(semester) }

    val timeEntities =
        courses.flatMap { course ->
            course.classTimes.map { time ->
                time.toEntity(
                    semester = semester,
                    serialNo = course.serialNo,
                )
            }
        }

    val teacherEntities =
        courses.flatMap { course ->
            course.teachers.mapIndexed { position, teacherName ->
                CourseTeacherEntity(
                    semester = semester,
                    serialNo = course.serialNo.value,
                    position = position,
                    teacherName = teacherName,
                )
            }
        }

    return LocalCatalogEntities(
        catalog =
        CourseCatalogEntity(
            semester = semester,
            lastUpdatedEpochMillis = lastUpdated.toEpochMilliseconds(),
        ),
        courses = courseEntities,
        teachers = teacherEntities,
        courseTimes = timeEntities,
    )
}

private data class CourseKey(
    val semester: String,
    val serialNo: String,
)

internal fun LocalCatalogEntities.toDomain(): CourseResult {
    val timesByCourse =
        courseTimes.groupBy { time ->
            CourseKey(
                semester = time.semester,
                serialNo = time.serialNo,
            )
        }

    val teachersByCourse =
        teachers
            .sortedBy { it.position }
            .groupBy { teacher ->
                CourseKey(
                    semester = teacher.semester,
                    serialNo = teacher.serialNo,
                )
            }

    return CourseResult(
        lastUpdated = Instant.fromEpochMilliseconds(catalog.lastUpdatedEpochMillis),
        semester = catalog.semester,
        courses =
        courses.map { course ->
            val key =
                CourseKey(
                    semester = course.semester,
                    serialNo = course.serialNo,
                )

            course.toDomain(
                teachers = teachersByCourse[key].orEmpty().map { it.teacherName },
                classTimes =
                timesByCourse[key]
                    .orEmpty()
                    .mapNotNull { it.toDomainOrNull() }
                    .sortedWith(
                        compareBy(
                            { it.day.order },
                            { it.period.order },
                        ),
                    ),
            )
        },
    )
}
