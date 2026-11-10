package org.mpc.presentation

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Diversity3
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HowToVote
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SportsBasketball
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.mpc.domain.model.Club
import org.mpc.presentation.state.ClubUiState
import org.mpc.presentation.viewModel.ClubViewModel
import org.mpc.resources.Res
import org.mpc.resources.club_category_academic
import org.mpc.resources.club_category_department
import org.mpc.resources.club_category_recreation
import org.mpc.resources.club_category_self_governance
import org.mpc.resources.club_category_service
import org.mpc.resources.club_category_social
import org.mpc.resources.club_category_sports
import org.mpc.resources.clubs_all
import org.mpc.resources.clubs_failed_body
import org.mpc.resources.clubs_failed_title
import org.mpc.resources.clubs_location
import org.mpc.resources.clubs_no_backend_body
import org.mpc.resources.clubs_no_backend_title
import org.mpc.resources.clubs_retry
import org.mpc.resources.clubs_website

private const val COLLAPSED_DESCRIPTION_LINES = 3

@Composable
fun ClubsScreen(
    modifier: Modifier = Modifier,
    viewModel: ClubViewModel = metroViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ClubsView(state = state, onRetry = viewModel::load, modifier = modifier)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ClubsView(
    state: ClubUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        ClubUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }

        ClubUiState.NoBackend -> ClubsMessage(
            icon = Icons.Outlined.Dns,
            title = stringResource(Res.string.clubs_no_backend_title),
            body = stringResource(Res.string.clubs_no_backend_body),
            modifier = modifier,
        )

        ClubUiState.Failed -> ClubsMessage(
            icon = Icons.Outlined.CloudOff,
            title = stringResource(Res.string.clubs_failed_title),
            body = stringResource(Res.string.clubs_failed_body),
            modifier = modifier,
            action = {
                Button(onClick = onRetry, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Text(stringResource(Res.string.clubs_retry), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
                }
            },
        )

        is ClubUiState.Loaded -> ClubTabs(state.sections, modifier)
    }
}

@Composable
private fun ClubTabs(
    sections: List<Pair<String, List<Club>>>,
    modifier: Modifier,
) {
    val tabs = listOf(stringResource(Res.string.clubs_all)) + sections.map { categoryLabel(it.first) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val selectedIndex = selected.coerceIn(tabs.indices)
    val shown = if (selectedIndex == 0) sections else listOf(sections[selectedIndex - 1])

    Column(modifier = modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(selectedTabIndex = selectedIndex, edgePadding = 16.dp) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = index == selectedIndex,
                    onClick = { selected = index },
                    text = { Text(title) },
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            shown.forEach { (category, clubs) ->
                if (selectedIndex == 0) {
                    item(key = "header-$category") {
                        Text(
                            text = "${categoryLabel(category)} · ${clubs.size}",
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                items(clubs, key = { it.id }) { club -> ClubCard(club) }
            }
        }
    }
}

@Composable
private fun ClubCard(club: Club) {
    var expanded by rememberSaveable(club.id) { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    // The backend already translated name and description; the official English name is a subtitle unless it is the title.
    val subtitle = club.nameEn.takeIf { it != club.name }.orEmpty()

    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(16.dp).animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClubAvatar(club.categoryName)
                Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                    Text(club.name, style = MaterialTheme.typography.titleMedium)
                    if (subtitle.isNotBlank()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (club.description.isNotBlank()) {
                Text(
                    text = club.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_DESCRIPTION_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (club.address.isNotBlank() || club.website.isNotBlank()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    if (club.address.isNotBlank()) {
                        // Plain text, not a chip: the club site gives a room name, nothing to open.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Place,
                                contentDescription = stringResource(Res.string.clubs_location),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                club.address,
                                modifier = Modifier.padding(start = 4.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (club.website.isNotBlank()) {
                        AssistChip(
                            onClick = { uriHandler.openUri(club.website) },
                            label = { Text(stringResource(Res.string.clubs_website)) },
                            leadingIcon = { ChipIcon(Icons.AutoMirrored.Outlined.OpenInNew) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
}

/** The category's icon on a tonal circle; the club site's logos need an image loader the app does not have. */
@Composable
private fun ClubAvatar(categoryName: String) {
    Surface(
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(categoryIcon(categoryName), contentDescription = categoryLabel(categoryName), modifier = Modifier.size(24.dp))
        }
    }
}

/** The NCU club site's categories, keyed by the names the backend sends. */
private val CATEGORIES: Map<String, Pair<ImageVector, StringResource>> = mapOf(
    "聯誼性" to (Icons.Outlined.Diversity3 to Res.string.club_category_social),
    "學術性" to (Icons.Outlined.School to Res.string.club_category_academic),
    "康樂性" to (Icons.Outlined.Celebration to Res.string.club_category_recreation),
    "系學會" to (Icons.Outlined.Apartment to Res.string.club_category_department),
    "體育性" to (Icons.Outlined.SportsBasketball to Res.string.club_category_sports),
    "服務性" to (Icons.Outlined.VolunteerActivism to Res.string.club_category_service),
    "學生自治組織" to (Icons.Outlined.HowToVote to Res.string.club_category_self_governance),
)

private fun categoryIcon(categoryName: String): ImageVector = CATEGORIES[categoryName]?.first ?: Icons.Outlined.Groups

/** The category in the app's language; a category the app doesn't know keeps the backend's name. */
@Composable
private fun categoryLabel(categoryName: String): String = CATEGORIES[categoryName]?.let { stringResource(it.second) } ?: categoryName

@Composable
private fun ClubsMessage(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}
