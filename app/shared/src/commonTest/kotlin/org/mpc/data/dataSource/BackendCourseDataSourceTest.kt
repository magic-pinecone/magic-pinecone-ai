package org.mpc.data.dataSource

import androidx.compose.ui.text.intl.Locale
import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.mpc.core.installAppDefaults
import org.mpc.data.dto.CourseTypeDto
import org.mpc.data.dto.PasswordCardTypeDto
import org.mpc.domain.repository.BackendEndpointRepository
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

private const val BACKEND = "http://10.0.2.2:8000"

// What GET /api/v1/catalog/115-1/courses answers; see backend/app/api/routes/catalog.py.
private val catalogJson =
    """
    {
      "last_updated": "2026-11-09T19:28:17.336076Z",
      "academic_year": "115",
      "semester": "1",
      "courses": [
        {
          "serial_no": "01001",
          "class_no": "PE1011-A",
          "title": "大一體育",
          "credit": 0.0,
          "password_card": "OPTIONAL",
          "teachers": ["沈淑貞"],
          "class_times": ["1-3", "1-4"],
          "limit_cnt": 0,
          "admit_cnt": 0,
          "wait_cnt": 0,
          "college_name": "中心、處室",
          "department_name": "體育室",
          "course_type": "REQUIRED",
          "detail_url": "https://cis.ncu.edu.tw/Course/main/support/courseDetail.html?crs=01001"
        }
      ]
    }
    """.trimIndent()

private val detailJson =
    """
    {
      "serial_no": "01001",
      "objectives": "強健體魄",
      "content": "",
      "books": "",
      "teaching_method": "講授",
      "grading_policy": "出席 100%",
      "distribution_conditions": [{"priority": 1, "rule": "學制:限學士班。"}]
    }
    """.trimIndent()

private class Responder(
    private val status: HttpStatusCode = HttpStatusCode.OK,
    private val body: (String) -> String,
) {
    val requestedUrls = mutableListOf<String>()
    val requestedPaths get() = requestedUrls.map { it.substringBefore('?') }

    val client = HttpClient(
        MockEngine { request ->
            val url = request.url.toString()
            requestedUrls += url
            respond(
                content = body(url),
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        },
    ) {
        installAppDefaults()
    }
}

private class FixedBackendEndpoint(
    baseUrl: String?,
) : BackendEndpointRepository {
    override val baseUrl = MutableStateFlow(baseUrl)

    override suspend fun setBaseUrl(baseUrl: String?) {
        this.baseUrl.value = baseUrl
    }
}

private fun routing(
    responder: Responder,
    baseUrl: String?,
) = RoutingCourseDataSource(
    staticSource = StaticRemoteCourseDataSource(responder.client),
    backendSource = BackendCourseDataSource(responder.client),
    backendEndpoint = FixedBackendEndpoint(baseUrl),
)

val backendCourseDataSourceTests by testSuite {
    test("the catalog is read from the backend's catalog endpoint") {
        runTest {
            val responder = Responder { catalogJson }

            val result = BackendCourseDataSource(responder.client).getAllCourses(BACKEND, "115-1")

            assertEquals(listOf("$BACKEND/api/v1/catalog/115-1/courses"), responder.requestedPaths)
            assertEquals(Instant.parse("2026-11-09T19:28:17.336076Z"), result.lastUpdated)
            assertEquals("115", result.academicYear)
            assertEquals("1", result.semester)
            val course = result.courses.single()
            assertEquals("01001", course.serialNo)
            assertEquals(PasswordCardTypeDto.OPTIONAL, course.passwordCard)
            assertEquals(CourseTypeDto.REQUIRED, course.courseType)
            assertEquals(listOf("1-3", "1-4"), course.classTimes)
            assertEquals("體育室", course.departmentName)
        }
    }

    test("a course's syllabus is read from the backend's detail endpoint") {
        runTest {
            val responder = Responder { detailJson }

            val detail = BackendCourseDataSource(responder.client).getCourseDetails(BACKEND, "115-1", "01001")

            assertEquals(listOf("$BACKEND/api/v1/catalog/115-1/courses/01001/detail"), responder.requestedPaths)
            assertEquals("強健體魄", detail.objectives)
            assertEquals("", detail.books)
            assertEquals(1, detail.distributionConditions.single().priority)
        }
    }

    test("a semester the backend does not hold fails on its status") {
        runTest {
            val responder = Responder(HttpStatusCode.NotFound) { """{"detail": "Semester 114-2 is not available"}""" }

            assertFailsWith<ClientRequestException> {
                BackendCourseDataSource(responder.client).getAllCourses(BACKEND, "114-2")
            }
        }
    }

    test("courses come from the configured backend") {
        runTest {
            val responder = Responder { url -> if (url.contains("/detail")) detailJson else catalogJson }
            val source = routing(responder, baseUrl = BACKEND)

            source.getAllCourses("115-1")
            source.getCourseDetails("115-1", "01001")

            assertEquals(
                listOf(
                    "$BACKEND/api/v1/catalog/115-1/courses",
                    "$BACKEND/api/v1/catalog/115-1/courses/01001/detail",
                ),
                responder.requestedPaths,
            )
        }
    }

    test("both catalog endpoints are asked for the app's language") {
        runTest {
            val responder = Responder { url -> if (url.contains("/detail")) detailJson else catalogJson }
            val source = BackendCourseDataSource(responder.client)

            source.getAllCourses(BACKEND, "115-1")
            source.getCourseDetails(BACKEND, "115-1", "01001")

            val expected = "lang=${Locale.current.toLanguageTag()}"
            assertEquals(listOf(expected, expected), responder.requestedUrls.map { it.substringAfter('?') })
        }
    }

    test("courses come from the static catalog while no backend is configured") {
        runTest {
            val responder = Responder { url -> if (url.contains("/detail/")) detailJson else catalogJson }
            val source = routing(responder, baseUrl = null)

            source.getAllCourses("115-1")
            source.getCourseDetails("115-1", "01001")

            val staticCatalog = "https://raw.githubusercontent.com/magic-pinecone/magic-pinecone-lite/refs/heads/115-1"
            assertEquals(
                listOf("$staticCatalog/courses.json", "$staticCatalog/detail/01001.json"),
                responder.requestedUrls,
            )
        }
    }
}
