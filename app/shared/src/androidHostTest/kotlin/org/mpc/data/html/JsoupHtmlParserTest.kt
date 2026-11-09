package org.mpc.data.html

import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val BASE_URL = "https://example.test/app/"

private fun parse(html: String) = JsoupHtmlParser().parse(html, BASE_URL)

val jsoupHtmlParserTests by testSuite {
    test("select returns the matching elements in document order") {
        val links = parse("<a href='/one'>One</a><p><a href='/two'>Two</a></p><span>x</span>").select("a[href]")

        assertEquals(listOf("One", "Two"), links.map { it.text() })
    }

    test("select returns an empty list when nothing matches") {
        assertEquals(emptyList(), parse("<p>hi</p>").select("form#login_form"))
    }

    test("attr is null when absent and empty when present without a value") {
        val input = parse("<input disabled name='q'>").select("input").single()

        assertEquals("q", input.attr("name"))
        assertEquals("", input.attr("disabled"))
        assertNull(input.attr("value"))
    }

    test("absUrl resolves against the base URL and is null when absent") {
        val anchors = parse("<a href='next'>a</a><a href='/root'>b</a><a href='//other.test/x'>c</a><a>d</a>").select("a")

        assertEquals(
            listOf("https://example.test/app/next", "https://example.test/root", "https://other.test/x", null),
            anchors.map { it.absUrl("href") },
        )
    }

    test("text joins nested text and collapses whitespace") {
        assertEquals("Hello big world", parse("<p>Hello <b>big</b>\n  world</p>").select("p").single().text())
    }
}
