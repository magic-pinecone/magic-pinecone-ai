package org.mpc.domain.model

import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals

private fun article(
    id: String,
    topic: String,
    title: String = id,
    body: String = "text",
    isService: Boolean = false,
) = GovGuideArticle(id = id, title = title, org = "Agency", lang = "zh", topic = topic, url = "https://example.gov.tw/$id", isService = isService, body = body)

private val library = GovGuides(
    topics = listOf("work", "insurance"),
    articles = listOf(
        article("permit", "work", title = "Work permit", body = "20 hours a week"),
        article("portal", "work", isService = true, body = ""),
        article("summer", "work", body = "no limit in summer"),
        article("nhi", "insurance", body = "six months"),
    ),
)

val govGuidesTests by testSuite {
    test("a blank query keeps every topic and article in order") {
        assertEquals(
            listOf("work" to listOf("permit", "portal", "summer"), "insurance" to listOf("nhi")),
            library.shelves("  ").map { shelf -> shelf.topic to shelf.articles.map { it.id } },
        )
    }

    test("a query matches title, agency or text, ignoring case, and drops empty topics") {
        assertEquals(listOf("summer"), library.shelves("SUMMER").flatMap { it.articles }.map { it.id })
        assertEquals(listOf("permit"), library.shelves("work permit").flatMap { it.articles }.map { it.id })
        assertEquals(listOf("nhi"), library.shelves("six").flatMap { it.articles }.map { it.id })
        assertEquals(listOf("work"), library.shelves("hours").map { it.topic })
        assertEquals(emptyList(), library.shelves("visa"))
    }

    test("previous and next skip online services and cross topics") {
        assertEquals(null to library.articles[2], library.neighbours("permit"))
        assertEquals(library.articles[0] to library.articles[3], library.neighbours("summer"))
        assertEquals(library.articles[2] to null, library.neighbours("nhi"))
        assertEquals(null to null, library.neighbours("portal"))
    }
}
