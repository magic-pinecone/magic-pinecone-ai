package org.mpc.presentation.components

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import org.mpc.presentation.theme.MpcTheme
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSIndexPath
import platform.UIKit.UIColor
import platform.UIKit.UIContextualAction
import platform.UIKit.UIContextualActionStyle.UIContextualActionStyleDestructive
import platform.UIKit.UIFont
import platform.UIKit.UIFontTextStyleFootnote
import platform.UIKit.UILabel
import platform.UIKit.UISwipeActionsConfiguration
import platform.UIKit.UITableView
import platform.UIKit.UITableViewAutomaticDimension
import platform.UIKit.UITableViewCell
import platform.UIKit.UITableViewCellSelectionStyle.UITableViewCellSelectionStyleNone
import platform.UIKit.UITableViewCellStyle.UITableViewCellStyleSubtitle
import platform.UIKit.UITableViewDataSourceProtocol
import platform.UIKit.UITableViewDelegateProtocol
import platform.UIKit.UITableViewStyle.UITableViewStyleInsetGrouped
import platform.UIKit.UIUserInterfaceStyle.UIUserInterfaceStyleDark
import platform.UIKit.UIUserInterfaceStyle.UIUserInterfaceStyleLight
import platform.UIKit.row
import platform.UIKit.secondaryLabelColor
import platform.UIKit.secondarySystemGroupedBackgroundColor
import platform.darwin.NSObject
import kotlin.math.abs

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun AdaptiveList(
    items: List<AdaptiveListItem>,
    modifier: Modifier,
    onItemAction: (String) -> Unit,
) {
    // Retain the data source: UITableView's reference to it is weak.
    val dataSource = remember { ListDataSource() }
    var contentHeight by remember { mutableStateOf(1.0) }
    val listBackground = MpcTheme.colors.background
    val interfaceStyle =
        if (MpcTheme.colors.background.luminance() < 0.5f) {
            UIUserInterfaceStyleDark
        } else {
            UIUserInterfaceStyleLight
        }

    UIKitView(
        modifier = modifier.height(contentHeight.dp),
        properties =
        UIKitInteropProperties(
            interactionMode = UIKitInteropInteractionMode.NonCooperative,
            isNativeAccessibilityEnabled = true,
        ),
        factory = {
            ContentSizedTable { height ->
                if (abs(contentHeight - height) > CONTENT_HEIGHT_EPSILON) {
                    contentHeight = height
                }
            }.apply {
                this.dataSource = dataSource
                delegate = dataSource
                rowHeight = UITableViewAutomaticDimension
                estimatedRowHeight = ESTIMATED_ROW_HEIGHT
                allowsSelection = false
                backgroundColor = UIColor.clearColor
            }
        },
        update = { table ->
            table.overrideUserInterfaceStyle = interfaceStyle
            // An opaque background also covers the UIKit interop host's default
            // background, keeping the grouped list continuous with the sheet.
            table.backgroundColor = UIColor(
                red = listBackground.red.toDouble(),
                green = listBackground.green.toDouble(),
                blue = listBackground.blue.toDouble(),
                alpha = listBackground.alpha.toDouble(),
            )
            dataSource.onItemAction = onItemAction
            if (dataSource.items != items) {
                dataSource.items = items.toList()
                table.reloadData()
            }
        },
        onRelease = { table ->
            table.dataSource = null
            table.delegate = null
            table.onContentHeightChanged = null
        },
    )
}

@OptIn(ExperimentalForeignApi::class)
private class ContentSizedTable(
    var onContentHeightChanged: ((Double) -> Unit)?,
) : UITableView(frame = CGRectZero.readValue(), style = UITableViewStyleInsetGrouped) {
    override fun layoutSubviews() {
        super.layoutSubviews()
        onContentHeightChanged?.invoke(contentSize.useContents { height }.coerceAtLeast(1.0))
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class ListDataSource :
    NSObject(),
    UITableViewDataSourceProtocol,
    UITableViewDelegateProtocol {
    var items: List<AdaptiveListItem> = emptyList()
    var onItemAction: (String) -> Unit = {}

    override fun tableView(
        tableView: UITableView,
        numberOfRowsInSection: Long,
    ): Long = items.size.toLong()

    @ObjCSignatureOverride
    override fun tableView(
        tableView: UITableView,
        cellForRowAtIndexPath: NSIndexPath,
    ): UITableViewCell {
        val item = items[cellForRowAtIndexPath.row.toInt()]
        val cell =
            tableView.dequeueReusableCellWithIdentifier("AdaptiveListItem")
                ?: UITableViewCell(
                    style = UITableViewCellStyleSubtitle,
                    reuseIdentifier = "AdaptiveListItem",
                )
        val content = cell.defaultContentConfiguration()
        content.text = item.title
        content.secondaryText = item.supportingText
        content.textProperties.numberOfLines = 0
        content.secondaryTextProperties.numberOfLines = 0
        cell.contentConfiguration = content
        cell.selectionStyle = UITableViewCellSelectionStyleNone
        cell.backgroundColor = UIColor.secondarySystemGroupedBackgroundColor
        cell.accessoryView = UILabel().apply {
            text = item.trailingText
            font = UIFont.preferredFontForTextStyle(UIFontTextStyleFootnote)
            adjustsFontForContentSizeCategory = true
            textColor = UIColor.secondaryLabelColor
            sizeToFit()
        }
        return cell
    }

    @ObjCSignatureOverride
    override fun tableView(
        tableView: UITableView,
        trailingSwipeActionsConfigurationForRowAtIndexPath: NSIndexPath,
    ): UISwipeActionsConfiguration? {
        val item = items[trailingSwipeActionsConfigurationForRowAtIndexPath.row.toInt()]
        val actionLabel = item.actionLabel ?: return null
        val action = UIContextualAction.contextualActionWithStyle(
            style = UIContextualActionStyleDestructive,
            title = actionLabel,
        ) { _, _, completion ->
            // Capture the stable ID, since rows can move while a swipe is open.
            onItemAction(item.id)
            completion?.invoke(true)
        }
        return UISwipeActionsConfiguration.configurationWithActions(listOf(action))
    }
}

private const val CONTENT_HEIGHT_EPSILON = 0.5
private const val ESTIMATED_ROW_HEIGHT = 100.0
