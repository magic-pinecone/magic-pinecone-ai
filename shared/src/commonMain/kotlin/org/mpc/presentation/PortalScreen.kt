package org.mpc.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCardOff
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.mpc.data.portal.portalHome
import org.mpc.domain.model.PortalShortcutDestination
import org.mpc.domain.model.PortalShortcutIcon
import org.mpc.domain.model.PortalShortcutItem
import org.mpc.domain.model.PortalShortcutSection
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.presentation.theme.MpcTheme
import org.mpc.presentation.theme.platformTimetablePalette
import org.mpc.presentation.viewModel.PortalViewModel

@Composable
fun PortalScreen(
    onOpenDestination: (PortalShortcutDestination) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PortalViewModel = metroViewModel(),
) {
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var isEditing by rememberSaveable { mutableStateOf(false) }
    val home = remember(favoriteIds, query) { portalHome(favoriteIds.orEmpty(), query) }
    val favoriteIdSet = remember(favoriteIds) { favoriteIds.orEmpty().toSet() }
    val isSearching = query.isNotBlank()

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = MpcSpacing.space4, vertical = MpcSpacing.space2),
    ) {
        item(key = "search") {
            PortalSearchField(query = query, onQueryChange = { query = it })
        }
        item(key = "account") {
            PortalAccountCard(
                onOpenDestination = onOpenDestination,
                modifier = Modifier.fillMaxWidth().padding(top = MpcSpacing.space4),
            )
        }
        if (isSearching && home.isEmpty) {
            item(key = "no-results") {
                Text(
                    text = "找不到「${query.trim()}」相關的服務",
                    modifier = Modifier.fillMaxWidth().padding(top = MpcSpacing.space8),
                    textAlign = TextAlign.Center,
                    color = MpcTheme.colors.labelSecondary,
                )
            }
        }
        // Wait for the stored favorites so the defaults never flash before a customized list.
        if (favoriteIds != null && (!isSearching || home.favorites.isNotEmpty())) {
            favoritesSection(
                favorites = home.favorites,
                // Moves index into the full list, so filtered favorites must not offer them.
                isEditing = isEditing && !isSearching,
                onToggleEditing = { isEditing = !isEditing }.takeUnless { isSearching },
                onOpenDestination = onOpenDestination,
                viewModel = viewModel,
            )
        }
        catalogSections(
            sections = home.sections,
            favoriteIds = favoriteIdSet,
            isEditing = isEditing,
            onOpenDestination = onOpenDestination,
            viewModel = viewModel,
        )
    }
}

private fun LazyListScope.favoritesSection(
    favorites: List<PortalShortcutItem>,
    isEditing: Boolean,
    // Null while searching, when the favorites shown are only the matches.
    onToggleEditing: (() -> Unit)?,
    onOpenDestination: (PortalShortcutDestination) -> Unit,
    viewModel: PortalViewModel,
) {
    item(key = "favorites-header") {
        PortalSectionHeader(title = "常用") {
            if (onToggleEditing != null) {
                TextButton(onClick = onToggleEditing) {
                    Text(if (isEditing) "完成" else "編輯")
                }
            }
        }
    }
    if (favorites.isEmpty()) {
        item(key = "favorites-empty") {
            Text(
                text = "點「編輯」後按 ＋，即可把服務加入常用",
                modifier = Modifier.padding(horizontal = MpcSpacing.space4),
                style = MpcTheme.typography.supporting,
                color = MpcTheme.colors.labelSecondary,
            )
        }
    }
    itemsIndexed(favorites, key = { _, item -> "favorite/${item.id}" }) { index, item ->
        PortalShortcutRow(
            item = item,
            index = index,
            count = favorites.size,
            onClick = { onOpenDestination(item.destination) }.takeUnless { isEditing },
            trailingContent = if (isEditing) {
                {
                    FavoriteEditControls(
                        label = item.label,
                        canMoveUp = index > 0,
                        canMoveDown = index < favorites.lastIndex,
                        onMove = { offset -> viewModel.moveFavorite(item.id, offset) },
                        onRemove = { viewModel.removeFavorite(item.id) },
                    )
                }
            } else {
                null
            },
        )
    }
}

private fun LazyListScope.catalogSections(
    sections: List<PortalShortcutSection>,
    favoriteIds: Set<String>,
    isEditing: Boolean,
    onOpenDestination: (PortalShortcutDestination) -> Unit,
    viewModel: PortalViewModel,
) {
    sections.forEach { section ->
        item(key = "section/${section.title}") {
            PortalSectionHeader(title = section.title)
        }
        itemsIndexed(section.items, key = { _, item -> "${section.title}/${item.id}" }) { index, item ->
            val isFavorite = item.id in favoriteIds
            PortalShortcutRow(
                item = item,
                index = index,
                count = section.items.size,
                onClick = { onOpenDestination(item.destination) }.takeUnless { isEditing },
                trailingContent = if (isEditing) {
                    {
                        AddFavoriteButton(
                            label = item.label,
                            isFavorite = isFavorite,
                            onAdd = { viewModel.addFavorite(item.id) },
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

/** Adds a service to the favorites; one already there shows a check, and is removed from the favorites section. */
@Composable
private fun AddFavoriteButton(
    label: String,
    isFavorite: Boolean,
    onAdd: () -> Unit,
) {
    IconButton(onClick = onAdd, enabled = !isFavorite) {
        Icon(
            imageVector = if (isFavorite) Icons.Default.CheckCircle else Icons.Default.AddCircle,
            contentDescription = if (isFavorite) "已在常用：$label" else "加入常用：$label",
            tint = if (isFavorite) MpcTheme.colors.labelTertiary else MpcTheme.colors.accent,
        )
    }
}

@Composable
private fun PortalSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("搜尋服務") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "清除搜尋")
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MpcTheme.colors.surfaceSelected,
            unfocusedContainerColor = MpcTheme.colors.surfaceSelected,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun PortalSectionHeader(
    title: String,
    trailingContent: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(start = MpcSpacing.space4, top = MpcSpacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MpcTheme.typography.label,
            color = MpcTheme.colors.accent,
        )
        trailingContent()
    }
}

/** One row of a grouped list: rounded at the group's ends, with a small gap between rows. */
@Composable
private fun PortalShortcutRow(
    item: PortalShortcutItem,
    index: Int,
    count: Int,
    onClick: (() -> Unit)?,
    trailingContent: (@Composable () -> Unit)?,
) {
    ListItem(
        headlineContent = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { PortalShortcutBadge(item) },
        trailingContent = trailingContent,
        colors = ListItemDefaults.colors(containerColor = MpcTheme.colors.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (index == 0) 0.dp else SEGMENT_GAP)
            .clip(segmentShape(index, count))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    )
}

/** The icon on its category color, taken from the palette the timetable uses so the app shares one set of hues. */
@Composable
private fun PortalShortcutBadge(item: PortalShortcutItem) {
    val palette = platformTimetablePalette(
        MpcTheme.colors.background.luminance() < DARK_SURFACE_LUMINANCE_THRESHOLD,
    )
    // The palette lists BLUE, GREEN, ORANGE, PURPLE, TEAL first, matching the tint order.
    val colors = palette[item.tint.ordinal % palette.size]
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(colors.container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = item.icon.toImageVector(),
            contentDescription = null,
            tint = colors.content,
            modifier = Modifier.size(22.dp),
        )
    }
}

private const val DARK_SURFACE_LUMINANCE_THRESHOLD = 0.5f

@Composable
private fun FavoriteEditControls(
    label: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    Row {
        IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移$label")
        }
        IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移$label")
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Default.RemoveCircleOutline,
                contentDescription = "從常用移除$label",
                tint = MpcTheme.colors.destructive,
            )
        }
    }
}

private fun segmentShape(index: Int, count: Int): Shape {
    val top = if (index == 0) OUTER_CORNER else INNER_CORNER
    val bottom = if (index == count - 1) OUTER_CORNER else INNER_CORNER
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

private val OUTER_CORNER = 20.dp
private val INNER_CORNER = 4.dp
private val SEGMENT_GAP = 2.dp

private fun PortalShortcutIcon.toImageVector(): ImageVector = portalShortcutIcons.getValue(this)

private val portalShortcutIcons = mapOf(
    PortalShortcutIcon.ACCOUNT_BALANCE to Icons.Default.AccountBalance,
    PortalShortcutIcon.APPS to Icons.Default.Apps,
    PortalShortcutIcon.BADGE to Icons.Default.Badge,
    PortalShortcutIcon.BOOK to Icons.Default.Book,
    PortalShortcutIcon.CREDIT_CARD_OFF to Icons.Default.CreditCardOff,
    PortalShortcutIcon.EVENT to Icons.Default.Event,
    PortalShortcutIcon.GRADE to Icons.Default.Grade,
    PortalShortcutIcon.HEADSET to Icons.Default.HeadsetMic,
    PortalShortcutIcon.LOCAL_LIBRARY to Icons.Default.LocalLibrary,
    PortalShortcutIcon.MAIL to Icons.Default.Mail,
    PortalShortcutIcon.REQUEST_QUOTE to Icons.Default.RequestQuote,
    PortalShortcutIcon.SAVINGS to Icons.Default.Savings,
    PortalShortcutIcon.SCHOOL to Icons.Default.School,
    PortalShortcutIcon.SUPPORT_AGENT to Icons.Default.SupportAgent,
    PortalShortcutIcon.WARNING to Icons.Default.WarningAmber,
    PortalShortcutIcon.WIFI to Icons.Default.Wifi,
    PortalShortcutIcon.WORKSPACES to Icons.Default.Workspaces,
    PortalShortcutIcon.WORKSPACE_PREMIUM to Icons.Default.WorkspacePremium,
)
