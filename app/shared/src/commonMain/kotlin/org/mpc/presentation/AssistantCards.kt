package org.mpc.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.icerock.moko.resources.compose.stringResource
import org.mpc.domain.model.AssistantBusProblem
import org.mpc.domain.model.AssistantBusRoute
import org.mpc.domain.model.AssistantBusStop
import org.mpc.domain.model.AssistantDestination
import org.mpc.domain.model.AssistantPassage
import org.mpc.domain.model.AssistantPlace
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.presentation.theme.MpcTheme
import org.mpc.shared.MR
import org.mpc.shared.assistant_bus_in_minutes
import org.mpc.shared.assistant_bus_next_at
import org.mpc.shared.assistant_bus_no_estimate
import org.mpc.shared.assistant_bus_none_today
import org.mpc.shared.assistant_bus_not_configured
import org.mpc.shared.assistant_bus_rate_limited
import org.mpc.shared.assistant_bus_route
import org.mpc.shared.assistant_bus_unreachable
import org.mpc.shared.assistant_open_source
import org.mpc.shared.assistant_open_transit
import org.mpc.shared.assistant_open_walking
import org.mpc.shared.assistant_place_code

private const val SECONDS_PER_MINUTE = 60

/** Where the clock time sits in an ISO 8601 timestamp: `2026-11-10T16:19:00+08:00` → `16:19`. */
private val ISO_CLOCK = 11..15

@Composable
internal fun AssistantPlaceCard(place: AssistantPlace) {
    GraduationLanguageCard {
        AssistantCardTitle("${place.nameZh} · ${place.nameEn}")
        place.code?.let { AssistantNotice(stringResource(MR.strings.assistant_place_code, it)) }
        LinkButton(stringResource(MR.strings.assistant_open_walking), place.mapLinks.googleWalkingDirections)
    }
}

@Composable
internal fun AssistantDestinationCard(destination: AssistantDestination) {
    GraduationLanguageCard {
        AssistantCardTitle("${destination.name.zh} · ${destination.name.en}")
        LinkButton(stringResource(MR.strings.assistant_open_transit), destination.mapsUrl)
    }
}

@Composable
internal fun AssistantBusCard(route: AssistantBusRoute) {
    GraduationLanguageCard {
        AssistantCardTitle(stringResource(MR.strings.assistant_bus_route, route.route))
        route.stops.forEach { stop ->
            Row(horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space3)) {
                Text(
                    "${stop.stopName.zh} · ${stop.stopName.en}",
                    style = MpcTheme.typography.supporting,
                    color = MpcTheme.colors.label,
                    modifier = Modifier.weight(1f),
                )
                Text(stopStatus(stop), style = MpcTheme.typography.supporting, color = MpcTheme.colors.labelSecondary)
            }
        }
        if (route.source.isNotEmpty()) {
            Text(route.source, style = MpcTheme.typography.caption, color = MpcTheme.colors.labelSecondary)
        }
    }
}

@Composable
private fun stopStatus(stop: AssistantBusStop): String {
    val seconds = stop.estimateSeconds
    val next = stop.nextBusTime
    return when {
        stop.status == "EN_ROUTE" && seconds != null -> stringResource(
            MR.strings.assistant_bus_in_minutes,
            maxOf(1, (seconds + SECONDS_PER_MINUTE / 2) / SECONDS_PER_MINUTE),
        )

        stop.status == "NOT_DEPARTED" && next != null && next.length > ISO_CLOCK.last ->
            stringResource(MR.strings.assistant_bus_next_at, next.substring(ISO_CLOCK))

        stop.status == "LAST_BUS_GONE" || stop.status == "NO_SERVICE" -> stringResource(MR.strings.assistant_bus_none_today)

        else -> stringResource(MR.strings.assistant_bus_no_estimate)
    }
}

@Composable
internal fun assistantBusProblemText(problem: AssistantBusProblem): String = stringResource(
    when (problem) {
        AssistantBusProblem.NOT_CONFIGURED -> MR.strings.assistant_bus_not_configured
        AssistantBusProblem.RATE_LIMITED -> MR.strings.assistant_bus_rate_limited
        AssistantBusProblem.UNREACHABLE -> MR.strings.assistant_bus_unreachable
    },
)

@Composable
internal fun AssistantPassageCard(
    number: Int,
    passage: AssistantPassage,
) {
    GraduationLanguageCard {
        AssistantCardTitle("[$number] ${passage.title}")
        SelectionContainer {
            Text(passage.passage, style = MpcTheme.typography.supporting, color = MpcTheme.colors.labelSecondary)
        }
        LinkButton(stringResource(MR.strings.assistant_open_source), passage.url)
    }
}

@Composable
internal fun AssistantCardTitle(title: String) {
    Text(
        title,
        style = MpcTheme.typography.headline,
        color = MpcTheme.colors.label,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
internal fun AssistantNotice(text: String) {
    Text(text, style = MpcTheme.typography.supporting, color = MpcTheme.colors.labelSecondary)
}

@Composable
private fun LinkButton(
    label: String,
    url: String,
) {
    val uriHandler = LocalUriHandler.current
    OutlinedButton(onClick = { uriHandler.openUri(url) }) {
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
        Text(label, modifier = Modifier.padding(start = MpcSpacing.space2))
    }
}
