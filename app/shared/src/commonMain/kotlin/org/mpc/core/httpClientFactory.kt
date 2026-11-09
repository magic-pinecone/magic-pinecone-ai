package org.mpc.core

import io.ktor.client.HttpClient
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

@OptIn(ExperimentalSerializationApi::class)
fun createHttpClient(): HttpClient = // TODO: maybe assign platform specific engine via expect/actual could be an option
    HttpClient {
        install(Logging) {
            logger =
                object : Logger {
                    override fun log(message: String) {
                        co.touchlab.kermit.Logger
                            .d(tag = "HTTP_CLIENT") { message }
                    }
                }
            level = LogLevel.HEADERS
        }
        install(HttpCache) {
        }
        install(ContentNegotiation) {
            json(
                Json {
                    namingStrategy = JsonNamingStrategy.SnakeCase
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
                contentType = ContentType.Text.Plain,
            )

            json(
                Json {
                    namingStrategy = JsonNamingStrategy.SnakeCase
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
                contentType = ContentType.Application.Json,
            )
        }
    }
