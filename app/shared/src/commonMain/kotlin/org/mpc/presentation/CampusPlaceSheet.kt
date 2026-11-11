package org.mpc.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.compose.stringResource
import org.mpc.domain.model.CampusPlace
import org.mpc.domain.model.PriceRange
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.presentation.theme.MpcTheme
import org.mpc.presentation.theme.style
import org.mpc.shared.MR
import org.mpc.shared.campus_map_code
import org.mpc.shared.campus_map_inside
import org.mpc.shared.campus_map_open_apple
import org.mpc.shared.campus_map_opening_hours
import org.mpc.shared.campus_map_price
import org.mpc.shared.campus_map_price_range
import org.mpc.shared.campus_map_price_single
import org.mpc.shared.campus_map_walk_google

/** What the bottom sheet shows for one place: the names, code, description, hours, price, tags and the routes out. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CampusPlaceSheetContent(
    place: CampusPlace,
    languageTag: String,
    modifier: Modifier = Modifier,
    tenants: List<CampusPlace> = emptyList(),
    onSelectTenant: (String) -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current
    val name = place.name.resolve(languageTag)
    val category = place.category.style
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = MpcSpacing.space4),
        verticalArrangement = Arrangement.spacedBy(MpcSpacing.space3),
    ) {
        Column {
            Text(name, style = MpcTheme.typography.screenTitle, color = MpcTheme.colors.label)
            // The Chinese name is what the signs say, so it stays visible when the app shows another language.
            if (name != place.name.zh) {
                Text(place.name.zh, style = MpcTheme.typography.supporting, color = MpcTheme.colors.labelSecondary)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space2)) {
            Icon(category.icon, contentDescription = null, tint = category.color, modifier = Modifier.size(20.dp))
            val categoryName = stringResource(category.label)
            val code = place.code?.let { stringResource(MR.strings.campus_map_code, it) }
            Text(
                text = listOfNotNull(categoryName, code).joinToString(" · "),
                style = MpcTheme.typography.supporting,
                color = MpcTheme.colors.labelSecondary,
            )
        }
        place.description?.resolve(languageTag)?.takeIf { it.isNotBlank() }?.let { description ->
            Text(description, style = MpcTheme.typography.body, color = MpcTheme.colors.label)
        }
        place.openingHours?.let { hours ->
            Fact(Icons.Filled.Schedule, stringResource(MR.strings.campus_map_opening_hours)) {
                Text(hours, style = MpcTheme.typography.body, color = MpcTheme.colors.label)
            }
        }
        if (place.priceRange != null || place.priceNote != null) {
            Fact(Icons.Filled.Payments, stringResource(MR.strings.campus_map_price)) {
                place.priceRange?.let { range ->
                    Text(range.format(), style = MpcTheme.typography.headline, color = MpcTheme.colors.label)
                }
                place.priceNote?.resolve(languageTag)?.takeIf { it.isNotBlank() }?.let { note ->
                    Text(note, style = MpcTheme.typography.supporting, color = MpcTheme.colors.labelSecondary)
                }
            }
        }
        if (place.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space2)) {
                place.tags.sortedBy { it.ordinal }.forEach { tag ->
                    val style = tag.style
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(style.label)) },
                        leadingIcon = { Icon(style.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
        }
        if (tenants.isNotEmpty()) {
            Text(stringResource(MR.strings.campus_map_inside), style = MpcTheme.typography.sectionTitle, color = MpcTheme.colors.label)
            tenants.forEach { tenant -> TenantRow(tenant, languageTag, onClick = { onSelectTenant(tenant.id) }) }
        }
        Button(
            onClick = { uriHandler.openUri(place.mapLinks.googleWalkingDirections) },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Text(stringResource(MR.strings.campus_map_walk_google), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
        }
        OutlinedButton(
            onClick = { uriHandler.openUri(place.mapLinks.appleMaps) },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Text(stringResource(MR.strings.campus_map_open_apple), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
        }
    }
}

@Composable
private fun TenantRow(
    tenant: CampusPlace,
    languageTag: String,
    onClick: () -> Unit,
) {
    val style = tenant.category.style
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = MpcSpacing.space1),
        horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(style.icon, contentDescription = null, tint = style.color, modifier = Modifier.size(20.dp))
        Column {
            Text(tenant.name.resolve(languageTag), style = MpcTheme.typography.body, color = MpcTheme.colors.label)
            val subtitle = tenant.description?.resolve(languageTag)?.takeIf { it.isNotBlank() } ?: stringResource(style.label)
            Text(subtitle, style = MpcTheme.typography.caption, color = MpcTheme.colors.labelSecondary, maxLines = 2)
        }
    }
}

@Composable
private fun Fact(
    icon: ImageVector,
    label: String,
    content: @Composable () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space3)) {
        Icon(icon, contentDescription = null, tint = MpcTheme.colors.labelSecondary, modifier = Modifier.size(20.dp))
        Column {
            Text(label, style = MpcTheme.typography.caption, color = MpcTheme.colors.labelSecondary)
            content()
        }
    }
}

@Composable
private fun PriceRange.format(): String = if (min == max) {
    stringResource(MR.strings.campus_map_price_single, min)
} else {
    stringResource(MR.strings.campus_map_price_range, min, max)
}
