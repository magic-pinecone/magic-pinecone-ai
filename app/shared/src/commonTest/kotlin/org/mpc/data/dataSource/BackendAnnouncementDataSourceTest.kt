package org.mpc.data.dataSource

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.mpc.core.installAppDefaults
import org.mpc.data.dto.AnnouncementScopeDto
import org.mpc.data.dto.UserAffiliationDto
import org.mpc.data.repository.BackendNotConfiguredException
import org.mpc.data.repository.DefaultAnnouncementRepository
import org.mpc.domain.model.AnnouncementScope
import org.mpc.domain.model.UserAffiliation
import org.mpc.domain.repository.BackendEndpointRepository
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val BACKEND = "http://10.0.2.2:8000"

// What GET /api/v1/announcements answers; see backend/app/api/routes/announcements.py.
private val announcementsJson =
    """
    {
      "last_updated": "2026-11-10T04:00:00Z",
      "total": 2,
      "offset": 0,
      "limit": 500,
      "announcements": [
        {
          "announcement_id": "scholarship:1234",
          "scope": "AFFILIATION",
          "source_id": "scholarship",
          "category": "獎助學金",
          "unit_name": "學務處生活輔導組",
          "college_id": null,
          "college_name": "",
          "department_id": null,
          "department_name": "",
          "affiliations": ["UNDERGRADUATE", "GRADUATE"],
          "title": "115學年度優秀學生獎學金申請公告",
          "summary": {
            "獎助金額": "10000 元",
            "申請期限": "115-10-31"
          },
          "published_date": "2026-10-31",
          "url": "https://cis.ncu.edu.tw/Scholarship/main/news/announceDetail?id=1234",
          "attachment_url": "https://cis.ncu.edu.tw/Scholarship/main/news/downloadAttachment?id=1234",
          "is_pinned": true
        },
        {
          "announcement_id": "dept_csie:404-1100-10001",
          "scope": "DEPARTMENT",
          "source_id": "dept_csie",
          "category": "系所公告",
          "unit_name": "資訊工程學系",
          "college_id": "5000",
          "college_name": "資訊電機學院",
          "department_id": "5200",
          "department_name": "資訊工程學系",
          "affiliations": ["ALL"],
          "title": "115學年度資工系專題發表注意事項",
          "summary": {},
          "published_date": "2026-11-01",
          "url": "https://www.csie.ncu.edu.tw/p/404-1100-10001.php",
          "attachment_url": null,
          "is_pinned": false
        }
      ]
    }
    """.trimIndent()

private class AnnouncementResponder(
    private val status: HttpStatusCode = HttpStatusCode.OK,
    private val body: (String) -> String = { announcementsJson },
) {
    val requestedUrls = mutableListOf<String>()

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

private class MutableBackendEndpoint(
    baseUrl: String?,
) : BackendEndpointRepository {
    override val baseUrl = MutableStateFlow(baseUrl)

    override suspend fun setBaseUrl(baseUrl: String?) {
        this.baseUrl.value = baseUrl
    }
}

val backendAnnouncementDataSourceTests by testSuite {
    test("announcements are read from the backend's announcements endpoint") {
        runTest {
            val responder = AnnouncementResponder()

            val result = BackendAnnouncementDataSource(responder.client).getAnnouncements(BACKEND)

            assertEquals(listOf("$BACKEND/api/v1/announcements?limit=500"), responder.requestedUrls)
            assertEquals(Instant.parse("2026-11-10T04:00:00Z"), result.lastUpdated)
            assertEquals(2, result.total)
            assertEquals(2, result.announcements.size)

            val first = result.announcements[0]
            assertEquals("scholarship:1234", first.announcementId)
            assertEquals(AnnouncementScopeDto.AFFILIATION, first.scope)
            assertEquals(listOf(UserAffiliationDto.UNDERGRADUATE, UserAffiliationDto.GRADUATE), first.affiliations)
            assertEquals("10000 元", first.summary["獎助金額"])
            assertTrue(first.isPinned)

            val second = result.announcements[1]
            assertEquals("dept_csie:404-1100-10001", second.announcementId)
            assertEquals(AnnouncementScopeDto.DEPARTMENT, second.scope)
            assertEquals("資訊工程學系", second.departmentName)
            assertEquals("資訊電機學院", second.collegeName)
        }
    }

    test("repository caches announcements and requires a configured backend") {
        runTest {
            val responder = AnnouncementResponder()
            val endpoint = MutableBackendEndpoint(baseUrl = null)
            val repository = DefaultAnnouncementRepository(
                backendDataSource = BackendAnnouncementDataSource(responder.client),
                backendEndpoint = endpoint,
            )

            assertFailsWith<BackendNotConfiguredException> {
                repository.fetchAnnouncements()
            }

            endpoint.setBaseUrl(BACKEND)
            val feed = repository.fetchAnnouncements(forceRefresh = false)
            val cachedFeed = repository.fetchAnnouncements(forceRefresh = false)

            assertEquals(1, responder.requestedUrls.size)
            assertEquals(feed, cachedFeed)
            assertEquals(AnnouncementScope.AFFILIATION, feed.announcements[0].scope)
            assertEquals(
                listOf(UserAffiliation.UNDERGRADUATE, UserAffiliation.GRADUATE),
                feed.announcements[0].affiliations,
            )
            assertEquals(AnnouncementScope.DEPARTMENT, feed.announcements[1].scope)
        }
    }
}
