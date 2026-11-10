package org.mpc.data.dataSource

import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import org.mpc.domain.model.Credits
import org.mpc.domain.model.GovGuides
import org.mpc.domain.model.GraduationLanguage
import org.mpc.domain.model.ServiceLearning

/** Reads the international-student endpoints of a Magic Pinecone backend. */
@Inject
internal class IntlDataSource(
    private val httpClient: HttpClient,
) {
    suspend fun getGraduationLanguage(baseUrl: String): GraduationLanguage = httpClient
        .get("$baseUrl/api/v1/intl/graduation-language") {
            expectSuccess = true
        }.body<GraduationLanguage>()

    suspend fun getServiceLearning(baseUrl: String): ServiceLearning = httpClient
        .get("$baseUrl/api/v1/intl/service-learning") {
            expectSuccess = true
        }.body<ServiceLearning>()

    suspend fun getCredits(baseUrl: String): Credits = httpClient
        .get("$baseUrl/api/v1/intl/credits") {
            expectSuccess = true
        }.body<Credits>()

    suspend fun getGuides(
        baseUrl: String,
        language: String,
    ): GovGuides = httpClient
        .get("$baseUrl/api/v1/intl/guides") {
            expectSuccess = true
            parameter("lang", language)
        }.body<GovGuides>()
}
