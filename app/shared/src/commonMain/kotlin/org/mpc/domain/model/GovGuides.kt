package org.mpc.domain.model

import kotlinx.serialization.Serializable

/** Government guides for international students, as served by `GET /api/v1/intl/guides`. */
@Serializable
data class GovGuides(
    /** Topic ids in display order, e.g. `work`, `insurance`, `residence`, `help`. */
    val topics: List<String>,
    val articles: List<GovGuideArticle>,
)

@Serializable
data class GovGuideArticle(
    val id: String,
    val title: String,
    /** The issuing agency, or the newspaper for a news report. */
    val org: String,
    val lang: String,
    val topic: String,
    val url: String,
    /** An online service to open, not a page to read: [body] is empty. */
    val isService: Boolean = false,
    /** The day [body] was fetched from [url]. */
    val fetchedOn: String = "",
    val body: String = "",
    /** The agency's own wording when [title], [org] and [body] are a machine translation; null when they are it. */
    val original: GovGuideOriginal? = null,
)

@Serializable
data class GovGuideOriginal(
    /** The language the agency wrote in, e.g. `zh` or `en`. */
    val lang: String,
    val title: String,
    val org: String,
    val body: String,
)

/** One topic of the library and the articles under it, in the backend's order. */
data class GovGuideShelf(
    val topic: String,
    val articles: List<GovGuideArticle>,
)

/**
 * The topics with the articles whose title, agency or text contains [query], ignoring case, in the translation
 * or the original wording. A blank query keeps every article; a topic left without articles is dropped.
 */
fun GovGuides.shelves(query: String = ""): List<GovGuideShelf> {
    val needle = query.trim()
    return topics.mapNotNull { topic ->
        articles
            .filter { it.topic == topic }
            .filter { article -> needle.isEmpty() || article.searchableText().any { it.contains(needle, ignoreCase = true) } }
            .takeIf { it.isNotEmpty() }
            ?.let { GovGuideShelf(topic, it) }
    }
}

// A reader may search by the Chinese name they saw on a form as well as by the translation.
private fun GovGuideArticle.searchableText(): List<String> = listOfNotNull(title, org, body, original?.title, original?.org, original?.body)

/** The articles that have text to read, in reading order; online services are left out. */
fun GovGuides.readable(): List<GovGuideArticle> = topics.flatMap { topic -> articles.filter { it.topic == topic && !it.isService } }

/** The readable articles before and after [id], for Previous and Next; null at either end. */
fun GovGuides.neighbours(id: String): Pair<GovGuideArticle?, GovGuideArticle?> {
    val reading = readable()
    val index = reading.indexOfFirst { it.id == id }
    if (index < 0) return null to null
    return reading.getOrNull(index - 1) to reading.getOrNull(index + 1)
}
