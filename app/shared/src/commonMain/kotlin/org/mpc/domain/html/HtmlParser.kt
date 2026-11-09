package org.mpc.domain.html

/** Parses HTML into a tree that CSS selectors can query. Each platform supplies a native parser. */
interface HtmlParser {
    fun parse(html: String, baseUrl: String): HtmlElement
}

interface HtmlElement {
    fun select(cssQuery: String): List<HtmlElement>

    /** Null when the attribute is absent. */
    fun attr(name: String): String?

    /** The attribute resolved against the document's base URL; null when absent or unresolvable. */
    fun absUrl(name: String): String?

    fun text(): String

    /** The element's inner HTML. */
    fun html(): String

    /** The element's inner HTML with every link resolved against the document's base URL. */
    fun htmlWithAbsoluteLinks(): String
}
