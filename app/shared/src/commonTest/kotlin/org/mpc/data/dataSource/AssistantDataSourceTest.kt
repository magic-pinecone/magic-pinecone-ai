package org.mpc.data.dataSource

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.mpc.core.installAppDefaults
import org.mpc.domain.model.AssistantBusProblem
import org.mpc.domain.model.AssistantKind
import kotlin.test.assertEquals

private const val ASSISTANT_BACKEND = "http://10.0.2.2:8000"

// What POST /api/v1/assistant/ask answers; see backend/app/api/routes/assistant.py.
private val placeAnswerJson =
    """
    {
      "question": "Where is E6-A203?",
      "kind": "place",
      "search_query": "Where is E6-A203?",
      "translated": false,
      "passages": [
        {"id": "engineering-building-5", "title": "Engineering Building 5 (工程五館)",
         "url": "https://maps.example/e6", "fetched_on": null, "passage": "Building code: E6."}
      ],
      "places": [
        {"id": "engineering-building-5", "name_zh": "工程五館", "name_en": "Engineering Building 5", "code": "E6",
         "category": "academic", "latitude": 24.967, "longitude": 121.187, "osm_id": null, "aliases": ["工五館"],
         "note_en": null,
         "map_links": {"google_maps": "https://maps.example/p", "google_walking_directions": "https://maps.example/e6",
                       "apple_maps": "https://maps.example/a"}}
      ],
      "destinations": [],
      "bus": [],
      "bus_problem": null,
      "model": "gemma-4-e2b",
      "answer": "- E6-A203 is in Engineering Building 5 [1].",
      "checks": {"refused": false, "cited": [1], "citations_valid": true, "unsupported_numbers": []},
      "error": null,
      "seconds": 3.4
    }
    """.trimIndent()

// A bus question on a backend with neither a model nor a TDX key.
private val busAnswerJson =
    """
    {
      "question": "132?",
      "kind": "bus",
      "search_query": "132?",
      "translated": false,
      "passages": [],
      "places": [],
      "destinations": [
        {"id": "thsr-taoyuan-station", "category": "station",
         "name": {"zh": "高鐵桃園站", "en": "THSR Taoyuan Station"}, "address": null, "position": null, "phone": null,
         "hours": null, "official_url": null, "getting_there": {"zh": "搭 172", "en": "Take bus 172"},
         "bus_routes": ["172"], "sources": [], "checked_on": "2026-11-10", "maps_url": "https://maps.example/thsr"}
      ],
      "bus": [
        {"route": "172", "fetched_at": "2026-11-11T08:01:40Z", "source": "TDX (tdx.transportdata.tw)",
         "stops": [
           {"stop_uid": "TAO270", "stop_name": {"zh": "中央大學警衛室", "en": "NCU Guard Station"}, "position": null,
            "sequence": 15, "direction": 0, "on_campus": true, "status": "NOT_DEPARTED", "estimate_seconds": null,
            "next_bus_time": "2026-11-11T16:19:00+08:00", "plate_number": null, "updated_at": null}
         ]}
      ],
      "bus_problem": "rate_limited",
      "model": null,
      "answer": null,
      "checks": null,
      "error": null,
      "seconds": 0.1
    }
    """.trimIndent()

private fun clientAnswering(
    json: String,
    onRequest: suspend (method: HttpMethod, url: String, body: String) -> Unit = { _, _, _ -> },
) = HttpClient(
    MockEngine { request ->
        onRequest(request.method, request.url.toString(), request.body.toByteArray().decodeToString())
        respond(content = json, headers = headersOf(HttpHeaders.ContentType, "application/json"))
    },
) {
    installAppDefaults()
}

val assistantDataSourceTests by testSuite {
    test("a question is posted to the backend's assistant endpoint and the answer is read") {
        runTest {
            val requests = mutableListOf<Triple<HttpMethod, String, String>>()
            val client = clientAnswering(placeAnswerJson) { method, url, body -> requests += Triple(method, url, body) }

            val result = AssistantDataSource(client).ask(ASSISTANT_BACKEND, "Where is E6-A203?")

            val (method, url, body) = requests.single()
            assertEquals(HttpMethod.Post, method)
            assertEquals("$ASSISTANT_BACKEND/api/v1/assistant/ask", url)
            assertEquals("""{"question":"Where is E6-A203?"}""", body)
            assertEquals(AssistantKind.PLACE, result.kind)
            assertEquals("gemma-4-e2b", result.model)
            assertEquals("- E6-A203 is in Engineering Building 5 [1].", result.answer)
            assertEquals(listOf(1), result.checks?.cited)
            val place = result.places.single()
            assertEquals("E6", place.code)
            assertEquals("https://maps.example/e6", place.mapLinks.googleWalkingDirections)
            assertEquals("Building code: E6.", result.passages.single().passage)
        }
    }

    test("an answer without a model still carries the bus arrivals and why they are incomplete") {
        runTest {
            val result = AssistantDataSource(clientAnswering(busAnswerJson)).ask(ASSISTANT_BACKEND, "132?")

            assertEquals(AssistantKind.BUS, result.kind)
            assertEquals(null, result.answer)
            assertEquals(null, result.model)
            assertEquals(AssistantBusProblem.RATE_LIMITED, result.busProblem)
            assertEquals("https://maps.example/thsr", result.destinations.single().mapsUrl)
            val stop = result.bus
                .single()
                .stops
                .single()
            assertEquals("NOT_DEPARTED", stop.status)
            assertEquals("2026-11-11T16:19:00+08:00", stop.nextBusTime)
            assertEquals("NCU Guard Station", stop.stopName.en)
        }
    }
}
