package org.mpc.data.portal

import de.infix.testBalloon.framework.core.testSuite
import org.mpc.domain.model.PortalShortcutDestination
import kotlin.test.assertEquals

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
            sections = defaultPortalShortcutSections,
            query = "mail",
        )

        assertEquals(1, sections.size)
        assertEquals("NCU Mail", sections.single().items.single().label)
    }

    test("empty search preserves the catalog") {
        assertEquals(
            expected = defaultPortalShortcutSections,
            actual = filterPortalShortcutSections(defaultPortalShortcutSections, "  "),
        )
    }

    test("ee-class opens through the official Portal handoff") {
        val item = defaultPortalShortcutSections
            .first()
            .items
            .first { it.label == "新 ee-class" }

        assertEquals("https://portal.ncu.edu.tw/system/ncueeclass", item.destination.url)
    }

    test("the course selection system opens directly in the app browser") {
        val item = defaultPortalShortcutSections
            .first()
            .items
            .first { it.label == "選課系統" }

        assertEquals("https://cis.ncu.edu.tw/Course/", item.destination.url)
    }

    test("each category has its own color, shared by its services") {
        val tints = defaultPortalShortcutSections.map { section -> section.items.map { it.tint }.distinct().single() }

        assertEquals(tints.distinct(), tints)
    }

    test("the catalog lists each service once and every default favorite") {
        val ids = defaultPortalShortcutSections.flatMap { section -> section.items.map { it.id } }

        assertEquals(ids.distinct(), ids)
        assertEquals(emptyList(), defaultPortalFavoriteIds - ids.toSet())
    }
}
