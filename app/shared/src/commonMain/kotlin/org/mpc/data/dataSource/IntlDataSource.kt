package org.mpc.data.dataSource

import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import org.mpc.domain.model.GovGuides
import org.mpc.domain.model.GraduationLanguage

/** Reads the international-student endpoints of a Magic Pinecone backend. */
@Inject
internal class IntlDataSource(
    private val httpClient: HttpClient,
) {
    suspend fun getGraduationLanguage(baseUrl: String): GraduationLanguage = httpClient
        .get("$baseUrl/api/v1/intl/graduation-language") {
            expectSuccess = true
        }.body<GraduationLanguage>()

    suspend fun getGuides(
        baseUrl: String,
        language: String,
    ): GovGuides = httpClient
        .get("$baseUrl/api/v1/intl/guides") {
            expectSuccess = true
            parameter("lang", language)
        }.body<GovGuides>()
}
