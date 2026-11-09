package org.mpc.data.dto

import kotlinx.serialization.Serializable

/** One row of the backend's `GET /api/v1/clubs`. */
@Serializable
internal data class ClubDto(
    val clubId: Int,
    val categoryName: String,
    val name: String,
    val nameEn: String = "",
    val description: String = "",
    val address: String = "",
    val website: String = "",
    val pictureUrl: String = "",
)
