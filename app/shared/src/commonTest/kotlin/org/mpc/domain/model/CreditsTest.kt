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

// What GET /api/v1/intl/credits answers; see backend/app/services/credits.py.
private val creditsJson =
    """
    {
      "contact": {
        "name_zh": "教務處課務組",
        "name_en": "Division of Curriculum",
        "website": "https://pdc.adm.ncu.edu.tw/",
        "sources": [{"url": "https://pdc.adm.ncu.edu.tw/a.pdf", "title": "教務處公告", "fetched": "2026-11-10", "quote": "請洽課務組。"}]
      },
      "requirements": [
        {
          "id": "minimum-credits",
          "degrees": ["bachelor"],
          "title_zh": "至少 128 學分",
          "title_en": "At least 128 credits",
          "summary_zh": "摘要",
          "summary_en": "Summary",
          "credits": 128.0,
          "sources": [{"url": "https://pdc.adm.ncu.edu.tw/", "title": "課務組", "fetched": "2026-11-10", "quote": "一百二十八學分"}]
        },
        {
          "id": "micro-courses",
          "degrees": ["bachelor"],
          "title_zh": "微課程",
          "title_en": "Micro-courses",
          "summary_zh": "摘要",
          "summary_en": "Summary",
          "credits": 0.5,
          "sources": []
        },
        {
          "id": "physical-education",
          "degrees": ["bachelor"],
          "title_zh": "體育",
          "title_en": "Physical education",
          "summary_zh": "摘要",
          "summary_en": "Summary",
          "credits": 0,
          "sources": []
        },
        {
          "id": "curriculum-by-cohort",
          "degrees": ["bachelor"],
          "title_zh": "依入學年度",
          "title_en": "By entry year",
          "summary_zh": "摘要",
          "summary_en": "Summary",
          "credits": null,
          "sources": []
        }
      ],
      "open_questions": ["國文是 5 學分還是 6 學分？"]
    }
    """.trimIndent()

val creditsTests by testSuite {
    test("the endpoint's response becomes generic sourced rules, keeping whole, fractional, zero and missing credits") {
        runTest {
            val client = HttpClient(
                MockEngine {
                    respond(creditsJson, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                },
            ) {
                installAppDefaults()
            }

            val rules = IntlDataSource(client).getCredits("http://10.0.2.2:8000").toSourcedRules()

            assertEquals(LocalizedText("教務處課務組", "Division of Curriculum"), rules.contact.name)
            assertEquals(LocalizedText("至少 128 學分", "At least 128 credits"), rules.rules.first().title)
            assertEquals("一百二十八學分", rules.rules.first().sources.single().quote)
            assertEquals(listOf(128.0, 0.5, 0.0, null), rules.rules.map { it.credits })
            assertEquals(listOf("國文是 5 學分還是 6 學分？"), rules.openQuestions)
        }
    }

    test("whole credits drop the trailing zero and fractions stay") {
        assertEquals("128", formatCredits(128.0))
        assertEquals("0.5", formatCredits(0.5))
        assertEquals("2.5", formatCredits(2.5))
        assertEquals("0", formatCredits(0.0))
    }
}
