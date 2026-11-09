package org.mpc.navigation

import androidx.navigation3.runtime.NavKey

class AndroidNavigator(
    private val state: AndroidNavigationState,
) {
    fun navigate(route: NavKey) {
        if (route is TopLevelRoute && route in state.backStacks) {
            navigateToTopLevel(route)
        } else {
            state.currentBackStack.add(route)
        }
    }

    fun goBack(): Boolean {
        if (state.currentBackStack.size > 1) {
            state.currentBackStack.removeLastOrNull()
            return true
        }
        if (state.selectedTopLevelRoute != state.startRoute) {
            state.selectedTopLevelRoute = state.startRoute
            return true
        }
        return false
    }

    private fun navigateToTopLevel(route: NavKey) {
        if (route == state.selectedTopLevelRoute) {
            state.currentBackStack.subList(1, state.currentBackStack.size).clear()
        } else {
            state.selectedTopLevelRoute = route
        }
    }
}
