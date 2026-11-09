package org.mpc.domain.search

import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals

private val teachers = listOf("王小明", "王大同", "黃小華", "李大明")

val catalogNamesTests by testSuite {
    test("an exact name, in either script, means only that name") {
        assertEquals(listOf("黃小華"), matchCatalogNames("黄小华", teachers))
        assertEquals(listOf("王小明"), matchCatalogNames(" 王小明 ", teachers))
    }

    test("a partial name means every catalog name containing it") {
        assertEquals(listOf("王小明", "王大同"), matchCatalogNames("王", teachers))
    }

    test("a name with extra words means the catalog name inside it") {
        assertEquals(listOf("王小明"), matchCatalogNames("王小明老師", teachers))
    }

    test("a name the catalog lacks, or a blank one, means nothing") {
        assertEquals(emptyList(), matchCatalogNames("陳美玲", teachers))
        assertEquals(emptyList(), matchCatalogNames("  ", teachers))
    }
}
