@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.mpc.presentation.theme

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readValue
import kotlinx.cinterop.value
import platform.CoreGraphics.CGFloatVar
import platform.CoreGraphics.CGRectZero
import platform.UIKit.UIColor
import platform.UIKit.UITraitCollection
import platform.UIKit.UIUserInterfaceStyle.UIUserInterfaceStyleDark
import platform.UIKit.UIUserInterfaceStyle.UIUserInterfaceStyleLight
import platform.UIKit.UIView
import platform.UIKit.resolvedColorWithTraitCollection

/**
 * Runs [resolve] against the live UIKit traits (Increase Contrast, size class, …) with the appearance forced
 * to [darkTheme], and again whenever those traits change. A zero-size observer view in the hierarchy reports
 * the changes; until it is attached, the forced appearance alone keeps the first frame correct.
 */
@Composable
internal fun <T> rememberTraitResolved(darkTheme: Boolean, resolve: (UITraitCollection) -> T): T {
    val style = if (darkTheme) UIUserInterfaceStyleDark else UIUserInterfaceStyleLight
    val traitRevision = remember { mutableIntStateOf(0) }
    val observer = remember { TraitObserver() }

    UIKitView(
        modifier = Modifier.size(0.dp),
        properties = UIKitInteropProperties(isNativeAccessibilityEnabled = false),
        factory = {
            observer.onTraitsChanged = { traitRevision.intValue += 1 }
            observer
        },
        update = { view -> view.overrideUserInterfaceStyle = style },
        onRelease = { view -> view.onTraitsChanged = null },
    )

    return remember(style, traitRevision.intValue) {
        resolve(
            UITraitCollection.traitCollectionWithTraitsFromCollections(
                listOf(observer.traitCollection, UITraitCollection.traitCollectionWithUserInterfaceStyle(style)),
            ),
        )
    }
}

private class TraitObserver : UIView(frame = CGRectZero.readValue()) {
    var onTraitsChanged: (() -> Unit)? = null

    override fun traitCollectionDidChange(previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        onTraitsChanged?.invoke()
    }
}

/** Resolves a dynamic UIKit color for [traits], keeping its alpha. */
internal fun UIColor.resolvedComposeColor(traits: UITraitCollection): Color = memScoped {
    val red = alloc<CGFloatVar>()
    val green = alloc<CGFloatVar>()
    val blue = alloc<CGFloatVar>()
    val alpha = alloc<CGFloatVar>()
    if (!resolvedColorWithTraitCollection(traits).getRed(red.ptr, green.ptr, blue.ptr, alpha.ptr)) {
        return@memScoped Color.Black
    }
    // Extended-range (Display P3) components fall outside sRGB; clamp to the nearest sRGB color.
    Color(
        red = red.value.toFloat().coerceIn(0f, 1f),
        green = green.value.toFloat().coerceIn(0f, 1f),
        blue = blue.value.toFloat().coerceIn(0f, 1f),
        alpha = alpha.value.toFloat().coerceIn(0f, 1f),
    )
}
