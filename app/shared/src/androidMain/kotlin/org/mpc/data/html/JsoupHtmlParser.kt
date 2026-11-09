package org.mpc.data.html

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.mpc.domain.html.HtmlElement
import org.mpc.domain.html.HtmlParser

class JsoupHtmlParser : HtmlParser {
    override fun parse(html: String, baseUrl: String): HtmlElement = JsoupElement(Jsoup.parse(html, baseUrl))
}

private class JsoupElement(private val element: Element) : HtmlElement {
    override fun select(cssQuery: String): List<HtmlElement> = element.select(cssQuery).map(::JsoupElement)

    override fun attr(name: String): String? = if (element.hasAttr(name)) element.attr(name) else null

    override fun absUrl(name: String): String? = element.absUrl(name).ifEmpty { null }

    override fun text(): String = element.text()

    override fun html(): String = element.html()

    override fun htmlWithAbsoluteLinks(): String = element.clone().apply {
        select("a[href]").forEach { link -> link.absUrl("href").ifEmpty { null }?.let { link.attr("href", it) } }
    }.html()
}
