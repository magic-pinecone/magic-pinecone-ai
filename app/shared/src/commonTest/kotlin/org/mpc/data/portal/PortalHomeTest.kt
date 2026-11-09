package org.mpc.data.portal

import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val MAIL = "https://portal.ncu.edu.tw/system/129"
private const val EECLASS = "https://portal.ncu.edu.tw/system/ncueeclass"
private const val GRADES = "https://portal.ncu.edu.tw/system/incu-studentscore"

val portalHomeTests by testSuite {
    test("favorites keep the user's order and drop services the catalog no longer lists") {
        val home = portalHome(favoriteIds = listOf(MAIL, "https://portal.ncu.edu.tw/system/retired", EECLASS), query = "")

        assertEquals(listOf("NCU Mail", "新 ee-class"), home.favorites.map { it.label })
        assertEquals(defaultPortalShortcutSections, home.sections)
    }

    test("search filters favorites and the catalog alike") {
        val home = portalHome(favoriteIds = listOf(MAIL, EECLASS), query = " mail ")

        assertEquals(listOf("NCU Mail"), home.favorites.map { it.label })
        assertEquals(listOf("NCU Mail"), home.sections.flatMap { it.items }.map { it.label })
    }

    test("a search with no match leaves the home empty") {
        assertTrue(portalHome(favoriteIds = listOf(MAIL), query = "不存在的服務").isEmpty)
    }

    test("adding a favorite appends it once") {
        assertEquals(listOf(MAIL, EECLASS), listOf(MAIL).addingFavorite(EECLASS))
        assertEquals(listOf(MAIL), listOf(MAIL).addingFavorite(MAIL))
    }

    test("removing a favorite keeps the rest in order") {
        assertEquals(listOf(MAIL, GRADES), listOf(MAIL, EECLASS, GRADES).removingFavorite(EECLASS))
    }

    test("moving a favorite stops at either end") {
        val ids = listOf(MAIL, EECLASS, GRADES)

        assertEquals(listOf(EECLASS, MAIL, GRADES), ids.movingFavorite(EECLASS, -1))
        assertEquals(listOf(EECLASS, GRADES, MAIL), ids.movingFavorite(MAIL, 5))
        assertEquals(ids, ids.movingFavorite(MAIL, -1))
        assertEquals(ids, ids.movingFavorite("https://portal.ncu.edu.tw/system/unknown", 1))
    }
}
