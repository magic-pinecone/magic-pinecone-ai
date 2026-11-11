package org.mpc.presentation.preview

import org.mpc.domain.model.AssistantAnswer
import org.mpc.domain.model.AssistantBusRoute
import org.mpc.domain.model.AssistantBusStop
import org.mpc.domain.model.AssistantChecks
import org.mpc.domain.model.AssistantDestination
import org.mpc.domain.model.AssistantKind
import org.mpc.domain.model.AssistantMapLinks
import org.mpc.domain.model.AssistantPassage
import org.mpc.domain.model.AssistantPlace
import org.mpc.domain.model.LocalizedText

private const val E6_DIRECTIONS =
    "https://www.google.com/maps/dir/?api=1&destination=24.967%2C121.187&travelmode=walking"
private const val THSR_DIRECTIONS =
    "https://www.google.com/maps/dir/?api=1&destination=25.0137163%2C121.2141381&travelmode=transit"

/** Answers shaped like the backend's, for previews that run without one. */
internal object AssistantPreviewFixture {
    /** A building question, answered by a model. */
    val place = AssistantAnswer(
        question = "Where is E6-A203?",
        kind = AssistantKind.PLACE,
        passages = listOf(
            AssistantPassage(
                id = "engineering-building-5",
                title = "Engineering Building 5 (工程五館)",
                url = E6_DIRECTIONS,
                passage = "Engineering Building 5 (工程五館) is on the NCU campus.\n" +
                    "Building code: E6. Room numbers that begin with E6- are in this building.\n" +
                    "Also called: 工五館, 工五.",
            ),
        ),
        places = listOf(
            AssistantPlace(
                id = "engineering-building-5",
                nameZh = "工程五館",
                nameEn = "Engineering Building 5",
                code = "E6",
                mapLinks = AssistantMapLinks(googleWalkingDirections = E6_DIRECTIONS),
            ),
        ),
        model = "gemma-4-e2b",
        answer = "- E6-A203 is a classroom in Engineering Building 5 (工程五館) [1].",
        checks = AssistantChecks(cited = listOf(1), citationsValid = true),
    )

    /** A bus question on a backend without a model: what was looked up, and no written answer. */
    val busWithoutModel = AssistantAnswer(
        question = "下一班到高鐵的公車幾點？",
        kind = AssistantKind.BUS,
        destinations = listOf(
            AssistantDestination(
                id = "thsr-taoyuan-station",
                name = LocalizedText("高鐵桃園站", "THSR Taoyuan Station (High Speed Rail)"),
                gettingThere = LocalizedText(
                    "在校內搭 172 或 173 直達高鐵桃園站，車程約 15–20 分鐘。",
                    "Take bus 172 or 173 from campus straight to THSR Taoyuan Station; the ride takes about 15–20 minutes.",
                ),
                mapsUrl = THSR_DIRECTIONS,
            ),
        ),
        bus = listOf(
            AssistantBusRoute(
                route = "172",
                source = "TDX (tdx.transportdata.tw)",
                stops = listOf(
                    AssistantBusStop(LocalizedText("中央大學警衛室", "NCU Guard Station"), "EN_ROUTE", estimateSeconds = 240),
                    AssistantBusStop(LocalizedText("中大湖", "NCU Lake"), "EN_ROUTE", estimateSeconds = 300),
                    AssistantBusStop(
                        LocalizedText("中央大學後門", "NCU Back Gate"),
                        "NOT_DEPARTED",
                        nextBusTime = "2026-11-11T16:19:00+08:00",
                    ),
                ),
            ),
        ),
        passages = listOf(
            AssistantPassage(
                id = "thsr-taoyuan-station",
                title = "THSR Taoyuan Station (High Speed Rail) (高鐵桃園站)",
                url = THSR_DIRECTIONS,
                passage = "Take bus 172 or 173 from campus straight to THSR Taoyuan Station; " +
                    "the ride takes about 15–20 minutes.",
            ),
        ),
    )
}
