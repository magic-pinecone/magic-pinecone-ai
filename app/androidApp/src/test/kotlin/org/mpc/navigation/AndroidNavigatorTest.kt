package org.mpc.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.serialization.Serializable
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private data class AndroidNavigatorFixture(
    val state: AndroidNavigationState,
    val navigator: AndroidNavigator,
)

private fun androidNavigatorFixture(): AndroidNavigatorFixture {
    val state =
        AndroidNavigationState(
            startRoute = HomeRoot,
            selectedTopLevelRoute = mutableStateOf<NavKey>(HomeRoot),
            backStacks =
            topLevelRoutes.associateWith { route ->
                NavBackStack<NavKey>(route)
            },
        )
    return AndroidNavigatorFixture(
        state = state,
        navigator = AndroidNavigator(state),
    )
}

val androidNavigatorTests by testSuite {
    testFixture {
        androidNavigatorFixture()
    } asContextForEach {
        test("switching top-level routes preserves their stacks") {
            navigator.navigate(CoursePlanningRoot)
            navigator.navigate(TestDetail)
            navigator.navigate(PortalRoot)
            navigator.navigate(CoursePlanningRoot)

            assertEquals(listOf<NavKey>(CoursePlanningRoot, TestDetail), state.currentBackStack.toList())
        }

        test("reselecting a top-level route pops its stack to root") {
            navigator.navigate(CoursePlanningRoot)
            navigator.navigate(TestDetail)

            navigator.navigate(CoursePlanningRoot)

            assertEquals(listOf<NavKey>(CoursePlanningRoot), state.currentBackStack.toList())
        }

        test("back pops the current stack before returning Home") {
            navigator.navigate(CoursePlanningRoot)
            navigator.navigate(TestDetail)

            assertTrue(navigator.goBack())
            assertEquals(CoursePlanningRoot, state.selectedTopLevelRoute)
            assertEquals(listOf<NavKey>(CoursePlanningRoot), state.currentBackStack.toList())

            assertTrue(navigator.goBack())
            assertEquals(HomeRoot, state.selectedTopLevelRoute)
        }

        test("back at the Home root is not consumed") {
            assertFalse(navigator.goBack())
        }
    }
}

@Serializable
private data object TestDetail : AndroidRoute
