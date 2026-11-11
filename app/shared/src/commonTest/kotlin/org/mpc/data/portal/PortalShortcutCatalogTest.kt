package org.mpc.data.portal

import de.infix.testBalloon.framework.core.testSuite
import org.mpc.domain.model.PortalShortcutDestination
import org.mpc.shared.MR
import org.mpc.shared.portal_shortcut_course_selection
import org.mpc.shared.portal_shortcut_eeclass
import org.mpc.shared.portal_shortcut_mail
import kotlin.test.assertEquals

/** The catalog with the labels these tests look for; every other text gets a distinct placeholder. */
internal val testCatalog = portalShortcutSections { text ->
    when (text) {
        MR.strings.portal_shortcut_mail -> "NCU Mail"
        MR.strings.portal_shortcut_eeclass -> "新 ee-class"
        MR.strings.portal_shortcut_course_selection -> "選課系統"
        else -> "text ${portalShortcutTexts.indexOf(text)}"
    }
}

val portalShortcutCatalogTests by testSuite {
    test("portal paths normalize to the trusted portal host") {
        assertEquals(
            expected = "https://portal.ncu.edu.tw/system/129",
            actual = PortalShortcutDestination(
                title = "NCU Mail",
                url = org.mpc.domain.model.portalUrl("system/129"),
            ).url,
        )
    }

    test("search removes empty sections and matches labels case insensitively") {
        val sections = filterPortalShortcutSections(
            sections = testCatalog,
            query = "mail",
        )

        assertEquals(1, sections.size)
        assertEquals("NCU Mail", sections.single().items.single().label)
    }

    test("empty search preserves the catalog") {
        assertEquals(
            expected = testCatalog,
            actual = filterPortalShortcutSections(testCatalog, "  "),
        )
    }

    test("ee-class opens through the official Portal handoff") {
        val item = testCatalog
            .first()
            .items
            .first { it.label == "新 ee-class" }

        assertEquals("https://portal.ncu.edu.tw/system/ncueeclass", item.destination.url)
    }

    test("the course selection system opens directly in the app browser") {
        val item = testCatalog
            .first()
            .items
            .first { it.label == "選課系統" }

        assertEquals("https://cis.ncu.edu.tw/Course/", item.destination.url)
    }

    test("labels resolve through the given text, so they follow the app language") {
        val labels = portalShortcutSections { "label" }.flatMap { section -> section.items.map { it.label } }

        assertEquals(setOf("label"), labels.toSet())
        assertEquals(labels.size, testCatalog.sumOf { it.items.size })
    }

    test("each category has its own color, shared by its services") {
        val tints = testCatalog.map { section -> section.items.map { it.tint }.distinct().single() }

        assertEquals(tints.distinct(), tints)
    }

    test("the catalog lists each service once and every default favorite") {
        val ids = testCatalog.flatMap { section -> section.items.map { it.id } }

        assertEquals(ids.distinct(), ids)
        assertEquals(emptyList(), defaultPortalFavoriteIds - ids.toSet())
    }
}
