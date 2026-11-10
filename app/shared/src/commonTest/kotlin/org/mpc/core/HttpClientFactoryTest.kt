package org.mpc.core

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.request.get
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.assertFailsWith

val httpClientFactoryTests by testSuite {
    test("an engine Error, like the browser's failed fetch, surfaces as an IOException") {
        runTest {
            val client = HttpClient(MockEngine { throw Error("Fail to fetch") }) { installAppDefaults() }
            assertFailsWith<IOException> { client.get("http://localhost:8000/api/v1/clubs") }
        }
    }
}
