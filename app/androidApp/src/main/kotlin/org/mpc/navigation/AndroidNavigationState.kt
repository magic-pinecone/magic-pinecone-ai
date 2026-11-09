package org.mpc.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.savedstate.compose.serialization.serializers.MutableStateSerializer

@Composable
fun rememberAndroidNavigationState(
    startRoute: TopLevelRoute = HomeRoot,
    topLevelRoutes: Set<TopLevelRoute> = org.mpc.navigation.topLevelRoutes,
): AndroidNavigationState {
    val selectedTopLevelRoute =
        rememberSerializable(
            startRoute,
            topLevelRoutes,
            serializer = MutableStateSerializer(NavKeySerializer()),
        ) {
            mutableStateOf<NavKey>(startRoute)
        }
    val backStacks =
        topLevelRoutes.associateWith { route ->
            rememberNavBackStack(route)
        }

    return remember(startRoute, topLevelRoutes) {
        AndroidNavigationState(
            startRoute = startRoute,
            selectedTopLevelRoute = selectedTopLevelRoute,
            backStacks = backStacks,
        )
    }
}

class AndroidNavigationState(
    val startRoute: TopLevelRoute,
    selectedTopLevelRoute: MutableState<NavKey>,
    val backStacks: Map<TopLevelRoute, NavBackStack<NavKey>>,
) {
    var selectedTopLevelRoute: NavKey by selectedTopLevelRoute

    val currentBackStack: NavBackStack<NavKey>
        get() = checkNotNull(backStacks[requireNotNull(selectedTopLevelRoute as? TopLevelRoute)])

    @Composable
    fun toDecoratedEntries(entryProvider: (NavKey) -> NavEntry<NavKey>): List<NavEntry<NavKey>> {
        val decoratedEntries =
            backStacks.mapValues { (_, backStack) ->
                rememberDecoratedNavEntries(
                    backStack = backStack,
                    entryDecorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = entryProvider,
                )
            }

        return topLevelRoutesInUse().flatMap { route ->
            decoratedEntries[route].orEmpty()
        }
    }

    private fun topLevelRoutesInUse(): List<TopLevelRoute> = if (selectedTopLevelRoute == startRoute) {
        listOf(startRoute)
    } else {
        listOf(startRoute, requireNotNull(selectedTopLevelRoute as? TopLevelRoute))
    }
}
