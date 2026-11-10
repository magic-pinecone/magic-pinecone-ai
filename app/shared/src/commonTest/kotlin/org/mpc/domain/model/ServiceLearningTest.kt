package org.mpc.domain.model

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.mpc.core.installAppDefaults
import org.mpc.data.dataSource.IntlDataSource
import kotlin.test.assertEquals

// What GET /api/v1/intl/service-learning answers; see backend/app/services/service_learning.py.
private val serviceLearningJson =
    """
    {
      "contact": {
        "name_zh": "學務處服務學習發展中心",
        "name_en": "Center for Service Learning",
        "website": "https://service-learning.ncu.edu.tw/",
        "sources": [{"url": "https://pdc.adm.ncu.edu.tw/a.pdf", "title": "教務處公告", "fetched": "2026-11-10", "quote": "請洽服務學習發展中心。"}]
      },
      "requirements": [
        {
          "id": "course",
          "degrees": ["bachelor"],
          "title_zh": "服務學習課程",
          "title_en": "Service-learning course",
          "summary_zh": "修兩學期",
          "summary_en": "Two semesters",
          "sources": [{"url": "https://service-learning.ncu.edu.tw/", "title": "中心首頁", "fetched": "2026-11-10", "quote": "零學分必修"}]
        },
        {
          "id": "thesis",
          "degrees": ["bachelor", "graduate"],
          "title_zh": "兩者皆適用",
          "title_en": "Both",
          "summary_zh": "摘要",
          "summary_en": "Summary",
          "sources": []
        }
      ],
      "open_questions": ["外籍生可否折抵？"]
    }
    """.trimIndent()

val serviceLearningTests by testSuite {
    test("the endpoint's response becomes generic sourced rules") {
        runTest {
            val client = HttpClient(
                MockEngine {
                    respond(serviceLearningJson, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                },
            ) {
                installAppDefaults()
            }

            val rules = IntlDataSource(client).getServiceLearning("http://10.0.2.2:8000").toSourcedRules()

            assertEquals(LocalizedText("學務處服務學習發展中心", "Center for Service Learning"), rules.contact.name)
            assertEquals("請洽服務學習發展中心。", rules.contact.sources.single().quote)
            assertEquals(LocalizedText("服務學習課程", "Service-learning course"), rules.rules.first().title)
            assertEquals("零學分必修", rules.rules.first().sources.single().quote)
            assertEquals(listOf("外籍生可否折抵？"), rules.openQuestions)
        }
    }

    test("a degree keeps the rules that name it and the rules that name none") {
        val rules = SourcedRules(
            contact = RuleContact(LocalizedText("", ""), "", emptyList()),
            rules = listOf(
                SourcedRule("b", listOf(RuleDegree.BACHELOR), LocalizedText("", ""), LocalizedText("", ""), sources = emptyList()),
                SourcedRule("g", listOf(RuleDegree.GRADUATE), LocalizedText("", ""), LocalizedText("", ""), sources = emptyList()),
                SourcedRule("all", emptyList(), LocalizedText("", ""), LocalizedText("", ""), sources = emptyList()),
            ),
            openQuestions = emptyList(),
        )

        assertEquals(listOf("b", "all"), rules.forDegree(RuleDegree.BACHELOR).map { it.id })
        assertEquals(listOf("b", "g", "all"), rules.forDegree(null).map { it.id })
        assertEquals(listOf(RuleDegree.BACHELOR, RuleDegree.GRADUATE), rules.degrees)
    }
}
