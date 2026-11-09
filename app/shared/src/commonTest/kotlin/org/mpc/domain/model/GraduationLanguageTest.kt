package org.mpc.domain.model

import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals

private val management = LocalizedText("管理學院", "College of Management")
private val engineering = LocalizedText("工學院", "College of Engineering")

private fun rule(
    id: String,
    degree: Degree,
    college: LocalizedText,
    vararg toeic: LanguageThreshold,
) = GraduationLanguageRule(
    id = id,
    degree = degree,
    college = college,
    english = if (toeic.isEmpty()) emptyMap() else mapOf("toeic_lr" to toeic.toList()),
)

private val requirements = GraduationLanguage(
    verifiedOn = "2026-11-10",
    disclaimer = LocalizedText("", ""),
    tests = emptyMap(),
    general = GraduationLanguageGeneral(summary = LocalizedText("", "")),
    chinese = ChineseRequirements(summary = LocalizedText("", "")),
    rules = listOf(
        rule("bachelor-management", Degree.BACHELOR, management, LanguageThreshold("700")),
        rule("master-management-110", Degree.MASTER, management, LanguageThreshold("600", cohortMin = 110, cohortMax = 114)),
        rule("master-management-115", Degree.MASTER, management, LanguageThreshold("785", cohortMin = 115)),
        rule("master-imba", Degree.MASTER, management),
        rule("master-engineering", Degree.MASTER, engineering, LanguageThreshold("600", cohortMin = 110)),
    ),
    sources = emptyList(),
)

val graduationLanguageTests by testSuite {
    test("rules are narrowed to the selected degree and college") {
        assertEquals(
            listOf("bachelor-management"),
            requirements.rulesFor(Degree.BACHELOR, college = null, cohort = 115).map { it.id },
        )
        assertEquals(
            listOf("master-engineering"),
            requirements.rulesFor(Degree.MASTER, college = "工學院", cohort = 115).map { it.id },
        )
    }

    test("a rule written for other admission years is left out") {
        assertEquals(
            listOf("master-management-110", "master-imba"),
            requirements.rulesFor(Degree.MASTER, college = "管理學院", cohort = 114).map { it.id },
        )
        assertEquals(
            listOf("master-management-115", "master-imba"),
            requirements.rulesFor(Degree.MASTER, college = "管理學院", cohort = 115).map { it.id },
        )
    }

    test("a rule without thresholds applies to every admission year") {
        assertEquals(
            listOf("master-imba"),
            requirements.rulesFor(Degree.MASTER, college = "管理學院", cohort = 109).map { it.id },
        )
    }

    test("colleges are listed once per degree") {
        assertEquals(listOf(management, engineering), requirements.collegesFor(Degree.MASTER))
        assertEquals(emptyList(), requirements.collegesFor(Degree.PHD))
    }
}
