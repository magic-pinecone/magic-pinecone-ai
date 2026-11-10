package org.mpc.data.repository

import androidx.compose.ui.text.intl.Locale
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.flow.first
import org.mpc.data.dto.ClubDto
import org.mpc.domain.model.Club
import org.mpc.domain.repository.BackendEndpointRepository
import org.mpc.domain.repository.ClubRepository

// shortcut: no Room cache, every open hits the backend; cache it if the list must work offline.
@ContributesBinding(AppScope::class)
@Inject
internal class DefaultClubRepository(
    private val httpClient: HttpClient,
    private val backendEndpoint: BackendEndpointRepository,
) : ClubRepository {
    override suspend fun fetchClubs(language: String?): List<Club>? {
        val baseUrl = backendEndpoint.baseUrl.first() ?: return null
        return httpClient
            .get("$baseUrl/api/v1/clubs") {
                expectSuccess = true
                // The backend translates the club text into this language.
                parameter("lang", language ?: Locale.current.toLanguageTag())
            }.body<List<ClubDto>>()
            .map {
                Club(
                    id = it.clubId,
                    category = it.category,
                    categoryName = it.categoryName,
                    name = it.name,
                    nameEn = it.nameEn,
                    description = it.description,
                    address = it.address,
                    website = it.website,
                    pictureUrl = it.pictureUrl,
                )
            }
    }
}
