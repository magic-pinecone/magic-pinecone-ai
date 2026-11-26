package org.mpc.data.dataSource

import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import org.mpc.domain.model.AssistantAnswer

/** A small model on a server CPU can take most of a minute; the engines' own limits are far shorter. */
private const val ASK_TIMEOUT_MILLIS = 120_000L

/** Asks the assistant endpoint of a Magic Pinecone backend. */
@Inject
internal class AssistantDataSource(
    httpClient: HttpClient,
) {
    private val httpClient = httpClient.config {
        install(HttpTimeout) {
            requestTimeoutMillis = ASK_TIMEOUT_MILLIS
            socketTimeoutMillis = ASK_TIMEOUT_MILLIS
        }
    }

    suspend fun ask(
        baseUrl: String,
        question: String,
    ): AssistantAnswer = httpClient
        .post("$baseUrl/api/v1/assistant/ask") {
            expectSuccess = true
            contentType(ContentType.Application.Json)
            setBody(AskRequest(question))
        }.body<AssistantAnswer>()
}

@Serializable
private data class AskRequest(
    val question: String,
)
