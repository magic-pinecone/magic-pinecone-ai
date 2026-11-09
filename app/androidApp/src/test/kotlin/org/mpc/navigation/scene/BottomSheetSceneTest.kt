package org.mpc.navigation.scene

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import de.infix.testBalloon.framework.core.testSuite
import org.mpc.navigation.HomeRoot
import org.mpc.navigation.PortalRoot
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(ExperimentalMaterial3Api::class)
private fun bottomSheetScene(
    key: NavKey = HomeRoot,
    entry: NavEntry<NavKey> = NavEntry<NavKey>(key) {},
    onBack: () -> Unit = {},
): BottomSheetScene<NavKey> {
    val previousEntries = listOf(entry)
    return BottomSheetScene(
        key = key,
        previousEntries = previousEntries,
        overlaidEntries = previousEntries,
        entry = entry,
        modalBottomSheetProperties = ModalBottomSheetProperties(),
        onBack = onBack,
    )
}

val bottomSheetSceneTests by testSuite {
    test("callback changes do not change scene identity") {
        val entry = NavEntry<NavKey>(HomeRoot) {}
        val first = bottomSheetScene(entry = entry, onBack = {})
        val second = bottomSheetScene(entry = entry, onBack = {})

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    test("logical scene changes change scene identity") {
        val first = bottomSheetScene()
        val second = bottomSheetScene(key = PortalRoot)

        assertNotEquals(first, second)
    }
}
