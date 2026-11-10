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
)
