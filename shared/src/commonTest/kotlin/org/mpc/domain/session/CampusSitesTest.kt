package org.mpc.domain.session

import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

val campusSitesTests by testSuite {
    test("JavaScript stays scoped to https campus hosts") {
        assertTrue(CampusSites.allowsJavascript("https://portal.ncu.edu.tw/login"))
        assertTrue(CampusSites.allowsJavascript("https://portal.ncu.edu.tw/system/129"))
        assertTrue(CampusSites.allowsJavascript("https://ncueeclass.ncu.edu.tw/dashboard"))
        assertTrue(CampusSites.allowsJavascript("https://NCU.EDU.TW/"))
        assertFalse(CampusSites.allowsJavascript("https://portal.ncu.edu.tw.evil.example/system/ncueeclass"))
        assertFalse(CampusSites.allowsJavascript("https://evilncu.edu.tw/"))
        assertFalse(CampusSites.allowsJavascript("http://ncueeclass.ncu.edu.tw/dashboard"))
        assertFalse(CampusSites.allowsJavascript("https://ncueeclass.ncu.edu.tw:8443/dashboard"))
    }

    test("Portal launch confirmations continue to their /go page") {
        assertEquals("https://portal.ncu.edu.tw/system/129/go", portalLaunchUrl("https://portal.ncu.edu.tw/system/129"))
        assertEquals(
            "https://portal.ncu.edu.tw/system/incu-studentscore/go",
            portalLaunchUrl("https://portal.ncu.edu.tw/system/incu-studentscore?from=menu"),
        )
        assertNull(portalLaunchUrl("https://portal.ncu.edu.tw/system/129/go"))
        assertNull(portalLaunchUrl("https://portal.ncu.edu.tw/login"))
        assertNull(portalLaunchUrl("https://portal.ncu.edu.tw/system/"))
        assertNull(portalLaunchUrl("https://ncueeclass.ncu.edu.tw/system/129"))
        assertNull(portalLaunchUrl("http://portal.ncu.edu.tw/system/129"))
    }

    test("site lookup accepts only the exact https host") {
        assertEquals(CampusSites.Eeclass, CampusSites.forUrl("https://ncueeclass.ncu.edu.tw/dashboard/myTimeTable"))
        assertNull(CampusSites.forUrl("https://ncueeclass.ncu.edu.tw:8443/dashboard"))
        assertNull(CampusSites.forUrl("https://portal.ncu.edu.tw/"))
    }

    test("site paths reject unusual segments") {
        assertEquals("/dashboard/myTimeTable", SitePath.parse("/dashboard/myTimeTable")?.value)
        assertEquals(emptyList(), SitePath.of("/dashboard/myTimeTable").query)
        assertNull(SitePath.parse("dashboard"))
        assertNull(SitePath.parse("/../admin"))
        assertNull(SitePath.parse("/dashboard/"))
    }

    test("a request prints no link or field") {
        val request = SiteRequest.Form("/save/?ajaxAuth=secret", listOf("anticsrf" to "secret"))

        assertEquals("SiteRequest", request.toString())
    }

    test("paging queries round-trip in order") {
        val bulletins = SitePath.of("/dashboard/latestBulletin").withQuery("category" to "all", "condition" to "0", "page" to "2", "pageSize" to "20")

        assertEquals("/dashboard/latestBulletin?category=all&condition=0&page=2&pageSize=20", bulletins.value)
        assertEquals(listOf("category" to "all", "condition" to "0", "page" to "2", "pageSize" to "20"), bulletins.query)
        assertEquals(bulletins, SitePath.parse(bulletins.value))
        assertEquals("/dashboard/latestMaterial?page=3&size=20", SitePath.of("/dashboard/latestMaterial?page=3").withQuery("size" to "20").value)
    }

    test("site paths refuse token keys, unknown keys, and malformed queries") {
        listOf(
            "/bulletin/content?id=1&ajaxAuth=0123456789abcdef",
            "/media/download/1?auth=abc",
            "/homework/editReport?anticsrf=abc",
            "/dashboard?token=1",
            "/dashboard?page=2&",
            "/dashboard?page",
            "/dashboard?page=2=3",
            "/dashboard?page=%32",
            "/dashboard?",
        ).forEach { raw -> assertNull(SitePath.parse(raw), raw) }
        assertFailsWith<IllegalArgumentException> { SitePath.of("/course/homework/1").withQuery("ajaxAuth" to "abc") }
    }
}
