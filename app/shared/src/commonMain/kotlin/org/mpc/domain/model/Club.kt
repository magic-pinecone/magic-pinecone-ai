package org.mpc.domain.model

/** A student club from the NCU club site, as the backend mirrors it. */
data class Club(
    val id: Int,
    val categoryName: String,
    val name: String,
    val nameEn: String,
    val description: String,
    val address: String,
    val website: String,
    val pictureUrl: String,
)
