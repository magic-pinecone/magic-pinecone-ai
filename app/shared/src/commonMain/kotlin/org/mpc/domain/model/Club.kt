package org.mpc.domain.model

/** A student club from the NCU club site, as the backend mirrors it. */
data class Club(
    val id: Int,
    /** The club site's category code, A to G. */
    val category: String,
    /** The category as the backend names it in the requested language. */
    val categoryName: String,
    val name: String,
    val nameEn: String,
    val description: String,
    val address: String,
    val website: String,
    val pictureUrl: String,
)
