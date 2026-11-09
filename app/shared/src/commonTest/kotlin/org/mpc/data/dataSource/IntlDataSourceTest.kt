package org.mpc.data.dataSource

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.mpc.core.installAppDefaults
import org.mpc.domain.model.Degree
import org.mpc.domain.model.LanguageThreshold
import org.mpc.domain.model.LocalizedText
import org.mpc.domain.model.OtherLanguageTest
import kotlin.test.assertEquals

private const val BACKEND = "http://10.0.2.2:8000"

// What GET /api/v1/intl/graduation-language answers; see backend/app/api/routes/intl.py.
private val graduationLanguageJson =
    """
    {
      "verified_on": "2026-11-10",
      "disclaimer": {"zh": "僅供參考", "en": "For reference only"},
      "tests": {"toeic_lr": {"zh": "多益（聽讀）測驗", "en": "TOEIC Listening & Reading"}},
      "general": {
        "summary": {"zh": "摘要", "en": "Summary"},
        "procedure": ["填寫申請表"],
        "alternatives": ["進修英文"],
        "international_students": ["沒有外籍生例外"],
        "legal_basis": ["學則第 48 條"],
        "graduate": {
          "summary": {"zh": "各系所自訂", "en": "Set by each department"},
          "international_students": ["機械系免除"],
          "no_rule_found": ["理學院"],
          "not_checked": ["數學"]
        },
        "sources": ["lc-form-general"]
      },
      "chinese": {
        "summary": {"zh": "沒有華語門檻", "en": "No Chinese threshold"},
        "requirements": ["華語課程 6 學分"],
        "open_questions": ["兩份法規不一致"],
        "not_graduation_requirements": ["獎助學金"],
        "courses": "華語學分課程",
        "sources": []
      },
      "rules": [
        {
          "id": "bachelor-eecs",
          "degree": "bachelor",
          "college": {"zh": "資訊電機學院", "en": "College of Electrical Engineering and Computer Science"},
          "department": null,
          "apply_at": {"zh": "語言中心", "en": "Language Center"},
          "summary": null,
          "english": {
            "toeic_lr": [
              {"value": "600", "cohort_min": null, "cohort_max": 114, "note": null},
              {"value": "785", "cohort_min": 115, "cohort_max": null, "note": null}
            ]
          },
          "other_languages": [{"language": "越南文", "test": "國際越南語認證 IVPT", "requirement": "A1 基礎"}],
          "other_languages_without_threshold": [],
          "alternatives": [],
          "international_students": [],
          "notes": ["以入學年度標準為主"],
          "sources": ["lc-form-general"]
        }
      ],
      "sources": [
        {
          "id": "lc-form-general",
          "title": "英文能力鑑定審核申請表",
          "url": "https://www.lc.ncu.edu.tw/form.pdf",
          "org": "語言中心",
          "lang": "zh",
          "degree_levels": ["bachelor"],
          "effective": "115 學年度第 1 學期起",
          "extract": "pdf"
        }
      ]
    }
    """.trimIndent()

val intlDataSourceTests by testSuite {
    test("the graduation language requirements are read from the backend's intl endpoint") {
        runTest {
            val requestedUrls = mutableListOf<String>()
            val client = HttpClient(
                MockEngine { request ->
                    requestedUrls += request.url.toString()
                    respond(
                        content = graduationLanguageJson,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
            ) {
                installAppDefaults()
            }

            val result = IntlDataSource(client).getGraduationLanguage(BACKEND)

            assertEquals(listOf("$BACKEND/api/v1/intl/graduation-language"), requestedUrls)
            assertEquals("2026-11-10", result.verifiedOn)
            assertEquals(LocalizedText("多益（聽讀）測驗", "TOEIC Listening & Reading"), result.tests["toeic_lr"])
            assertEquals(listOf("沒有外籍生例外"), result.general.internationalStudents)
            assertEquals(listOf("數學"), result.general.graduate?.notChecked)
            assertEquals(listOf("獎助學金"), result.chinese.notGraduationRequirements)
            val rule = result.rules.single()
            assertEquals(Degree.BACHELOR, rule.degree)
            assertEquals(null, rule.department)
            assertEquals("Language Center", rule.applyAt?.en)
            assertEquals(
                listOf(LanguageThreshold("600", cohortMax = 114), LanguageThreshold("785", cohortMin = 115)),
                rule.english["toeic_lr"],
            )
            assertEquals(listOf(OtherLanguageTest("越南文", "國際越南語認證 IVPT", "A1 基礎")), rule.otherLanguages)
            assertEquals("https://www.lc.ncu.edu.tw/form.pdf", result.sources.single().url)
        }
    }
}
