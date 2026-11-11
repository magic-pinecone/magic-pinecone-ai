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
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VerifiedUser
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
import org.mpc.domain.model.CampusUnit
import org.mpc.domain.model.FoodPlace
import org.mpc.domain.model.MapLinks
import org.mpc.domain.model.PriceRange
import org.mpc.presentation.theme.FoodMarkerColors
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.presentation.theme.MpcTheme
import org.mpc.presentation.theme.style
import org.mpc.shared.MR
import org.mpc.shared.campus_map_code
import org.mpc.shared.campus_map_halal_not_checked
import org.mpc.shared.campus_map_inside
import org.mpc.shared.campus_map_open_apple
import org.mpc.shared.campus_map_opening_hours
import org.mpc.shared.campus_map_price
import org.mpc.shared.campus_map_price_range
import org.mpc.shared.campus_map_price_single
import org.mpc.shared.campus_map_tag_halal
import org.mpc.shared.campus_map_units
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
        UnitsSection(place.units, languageTag)
        RouteButtons(place.mapLinks)
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
private fun UnitsSection(
    units: List<CampusUnit>,
    languageTag: String,
) {
    if (units.isEmpty()) return
    Text(stringResource(MR.strings.campus_map_units), style = MpcTheme.typography.sectionTitle, color = MpcTheme.colors.label)
    units.forEach { unit -> UnitRow(unit, languageTag) }
}

/** One unit in the building; tapping it opens the unit's website when it has one. */
@Composable
private fun UnitRow(
    unit: CampusUnit,
    languageTag: String,
) {
    val uriHandler = LocalUriHandler.current
    val name = unit.name.resolve(languageTag)
    val website = unit.website
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (website != null) Modifier.clickable { uriHandler.openUri(website) } else Modifier)
            .padding(vertical = MpcSpacing.space1),
        horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Apartment,
            contentDescription = null,
            tint = if (website != null) MpcTheme.colors.accent else MpcTheme.colors.labelSecondary,
            modifier = Modifier.size(20.dp),
        )
        Column {
            Text(name, style = MpcTheme.typography.body, color = MpcTheme.colors.label)
            // The Chinese name is what the door sign says, so it stays next to the floor.
            val subtitle = listOfNotNull(unit.name.zh.takeIf { it != name }, unit.floor).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MpcTheme.typography.caption, color = MpcTheme.colors.labelSecondary)
            }
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

@Composable
private fun RouteButtons(mapLinks: MapLinks) {
    val uriHandler = LocalUriHandler.current
    Button(
        onClick = { uriHandler.openUri(mapLinks.googleWalkingDirections) },
        modifier = Modifier.fillMaxWidth(),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Text(stringResource(MR.strings.campus_map_walk_google), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
    }
    if (platformHasAppleMaps) {
        OutlinedButton(
            onClick = { uriHandler.openUri(mapLinks.appleMaps) },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Text(stringResource(MR.strings.campus_map_open_apple), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
        }
    }
}

/** Whether the device has Apple Maps to open a place in. */
internal expect val platformHasAppleMaps: Boolean

/** What the bottom sheet shows for a restaurant: name, kind, address, phone, halal certificate and the routes out. */
@Composable
fun FoodPlaceSheetContent(
    place: FoodPlace,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = MpcSpacing.space4),
        verticalArrangement = Arrangement.spacedBy(MpcSpacing.space3),
    ) {
        Column {
            Text(place.name, style = MpcTheme.typography.screenTitle, color = MpcTheme.colors.label)
            place.kind?.let { Text(it, style = MpcTheme.typography.supporting, color = MpcTheme.colors.labelSecondary) }
        }
        val halal = place.halal
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space2)) {
            Icon(
                Icons.Filled.VerifiedUser,
                contentDescription = null,
                tint = if (halal != null) FoodMarkerColors.halal else MpcTheme.colors.labelTertiary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = if (halal != null) {
                    stringResource(MR.strings.campus_map_tag_halal) + ": " + (halal.certificates + halal.certifier).filter { it.isNotBlank() }.joinToString(" · ")
                } else {
                    stringResource(MR.strings.campus_map_halal_not_checked)
                },
                style = MpcTheme.typography.body,
                color = MpcTheme.colors.label,
            )
        }
        place.address?.let { address ->
            Row(horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space3)) {
                Icon(Icons.Filled.Place, contentDescription = null, tint = MpcTheme.colors.labelSecondary, modifier = Modifier.size(20.dp))
                Text(address, style = MpcTheme.typography.body, color = MpcTheme.colors.label)
            }
        }
        place.phone?.let { phone ->
            Row(
                modifier = Modifier.clickable { uriHandler.openUri("tel:" + phone.filter { it.isDigit() || it == '+' }) },
                horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space3),
            ) {
                Icon(Icons.Filled.Call, contentDescription = null, tint = MpcTheme.colors.accent, modifier = Modifier.size(20.dp))
                Text(phone, style = MpcTheme.typography.body, color = MpcTheme.colors.accent)
            }
        }
        RouteButtons(place.mapLinks)
    }
}
