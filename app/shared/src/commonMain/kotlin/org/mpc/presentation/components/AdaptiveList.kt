package org.mpc.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** A read-only row. IDs must be unique and stable within a list. */
data class AdaptiveListItem(
    val id: String,
    val title: String,
    val supportingText: String,
    val trailingText: String,
    val actionLabel: String? = null,
    val actionAccessibilityLabel: String? = null,
)

/**
 * A scrolling list of Material rows on Android and native UIKit cells on iOS.
 * Content wraps its height up to the caller's constraints; constrain the maximum
 * height to create a scrolling viewport. An optional trailing action is exposed
 * as a button and receives the row's stable ID.
 */
@Composable
expect fun AdaptiveList(
    items: List<AdaptiveListItem>,
    modifier: Modifier = Modifier,
    onItemAction: (String) -> Unit = {},
)
