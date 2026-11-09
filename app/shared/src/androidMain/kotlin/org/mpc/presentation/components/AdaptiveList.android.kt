package org.mpc.presentation.components

import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
actual fun AdaptiveList(
    items: List<AdaptiveListItem>,
    modifier: Modifier,
    onItemAction: (String) -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            SwipeableListRow(
                item = item,
                shape = ListItemDefaults.segmentedShapes(index, items.size).shape,
                onItemAction = onItemAction,
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SwipeableListRow(
    item: AdaptiveListItem,
    shape: Shape,
    onItemAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val revealDistance = with(density) { 88.dp.toPx() }
    val revealState = remember(revealDistance) {
        AnchoredDraggableState(false).apply {
            updateAnchors(
                DraggableAnchors {
                    false at 0f
                    true at -revealDistance
                },
            )
        }
    }
    val canAct = item.actionLabel != null
    var actionDispatched by remember(item.id, canAct) { mutableStateOf(false) }
    val performAction = {
        if (canAct && !actionDispatched) {
            actionDispatched = true
            onItemAction(item.id)
        }
    }
    LaunchedEffect(item.id, canAct, actionDispatched, revealState) {
        if (!canAct || actionDispatched) {
            revealState.snapTo(false)
            // A request may leave the item in the list. Allow a new interaction
            // after closing, while guarding duplicate dispatches until then.
            actionDispatched = false
        }
    }
    Box(modifier = modifier.clipToBounds()) {
        if (canAct && revealState.offset < 0f) {
            Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
                Button(
                    onClick = performAction,
                    modifier = Modifier.fillMaxHeight().width(80.dp).padding(vertical = 2.dp).semantics {
                        contentDescription = item.actionAccessibilityLabel ?: item.actionLabel
                    },
                    shape = CircleShape,
                    contentPadding = PaddingValues(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth()
                .padding(end = with(density) { (-revealState.requireOffset()).toDp() })
                .anchoredDraggable(revealState, Orientation.Horizontal, enabled = canAct)
                .semantics(mergeDescendants = true) {
                    if (canAct) {
                        customActions = listOf(
                            CustomAccessibilityAction(item.actionAccessibilityLabel ?: item.actionLabel) {
                                performAction()
                                true
                            },
                        )
                    }
                },
            shape = if (revealState.offset < 0f) MaterialTheme.shapes.large else shape,
            color = ListItemDefaults.segmentedColors().containerColor,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = item.trailingText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
