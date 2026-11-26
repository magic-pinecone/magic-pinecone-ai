package org.mpc.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What `POST /api/v1/assistant/ask` answers: what the backend looked up, and the model's answer when it has one. */
@Serializable
data class AssistantAnswer(
    val question: String,
    val kind: AssistantKind = AssistantKind.DOCUMENTS,
    /** What the model was given to answer from; `[1]` in [answer] is the first of these. */
    val passages: List<AssistantPassage> = emptyList(),
    val places: List<AssistantPlace> = emptyList(),
    val destinations: List<AssistantDestination> = emptyList(),
    val bus: List<AssistantBusRoute> = emptyList(),
    /** Why a bus question has no live arrivals. */
    val busProblem: AssistantBusProblem? = null,
    /** Null when the backend has no model; [answer] is then null too. */
    val model: String? = null,
    val answer: String? = null,
    val checks: AssistantChecks? = null,
    /** Why the model could not answer; what was looked up is still filled in. */
    val error: String? = null,
)

@Serializable
enum class AssistantKind {
    @SerialName("documents")
    DOCUMENTS,

    @SerialName("place")
    PLACE,

    @SerialName("bus")
    BUS,
}

@Serializable
enum class AssistantBusProblem {
    @SerialName("not_configured")
    NOT_CONFIGURED,

    @SerialName("rate_limited")
    RATE_LIMITED,

    @SerialName("unreachable")
    UNREACHABLE,
}

@Serializable
data class AssistantPassage(
    val id: String,
    val title: String,
    val url: String,
    val passage: String,
)

/** A campus building, as `GET /api/v1/campus/places` serves it. */
@Serializable
data class AssistantPlace(
    val id: String,
    val nameZh: String,
    val nameEn: String,
    /** The prefix of a classroom number such as `E6-A203`; null for places that hold no classes. */
    val code: String? = null,
    val mapLinks: AssistantMapLinks,
)

@Serializable
data class AssistantMapLinks(
    val googleWalkingDirections: String,
)

/** A place off campus, as `GET /api/v1/transit/destinations` serves it. */
@Serializable
data class AssistantDestination(
    val id: String,
    val name: LocalizedText,
    val gettingThere: LocalizedText,
    /** Opens public-transport directions from NCU. */
    val mapsUrl: String,
)

@Serializable
data class AssistantBusRoute(
    val route: String,
    val source: String = "",
    val stops: List<AssistantBusStop> = emptyList(),
)

@Serializable
data class AssistantBusStop(
    val stopName: LocalizedText,
    /** `EN_ROUTE`, `NOT_DEPARTED`, `SKIPPED`, `LAST_BUS_GONE`, `NO_SERVICE` or `UNKNOWN`. */
    val status: String,
    val estimateSeconds: Int? = null,
    /** ISO 8601 in Taipei time, e.g. `2026-11-10T16:19:00+08:00`. */
    val nextBusTime: String? = null,
)

@Serializable
data class AssistantChecks(
    /** The model said the passages do not hold the answer. */
    val refused: Boolean = false,
    val cited: List<Int> = emptyList(),
    val citationsValid: Boolean = false,
    /** Numbers in the answer that none of the passages contains. */
    val unsupportedNumbers: List<String> = emptyList(),
)
