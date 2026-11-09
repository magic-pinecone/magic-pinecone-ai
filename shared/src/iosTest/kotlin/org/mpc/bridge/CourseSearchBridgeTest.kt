package org.mpc.bridge

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.mpc.domain.model.CourseDay
import org.mpc.domain.model.CourseFilter
import org.mpc.domain.model.CoursePeriod
import org.mpc.domain.model.CourseTime
import org.mpc.domain.model.SlotMatch
import kotlin.test.assertEquals

val courseSearchBridgeTests by testSuite {
    test("catalog replays the latest snapshot after observation restarts") {
        runTest {
            val bridge = CourseSearchBridge()
            val received = mutableListOf<CourseCatalogSnapshot>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                bridge.catalogSnapshot.collect { received.add(it) }
            }
            val loaded = CourseCatalogSnapshot(isLoading = false)
            bridge.updateCatalogSnapshot(loaded)
            testScheduler.runCurrent()
            bridge.updateCatalogSnapshot(loaded.copy())
            testScheduler.runCurrent()
            assertEquals(listOf(CourseCatalogSnapshot(), loaded), received)

            collector.cancelAndJoin()
            val failed = loaded.copy(failureMessage = "offline")
            bridge.updateCatalogSnapshot(failed)
            testScheduler.runCurrent()
            assertEquals(listOf(CourseCatalogSnapshot(), loaded), received)

            val restartedCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                bridge.catalogSnapshot.collect { received.add(it) }
            }
            assertEquals(listOf(CourseCatalogSnapshot(), loaded, failed), received)
            restartedCollector.cancelAndJoin()
        }
    }

    test("actions sent before collection are delivered in order") {
        runTest {
            val bridge = CourseSearchBridge()
            val monday1 = CourseTime(CourseDay.MONDAY, CoursePeriod.ONE)
            val department = CourseFilter.Department("數學系")
            bridge.updateQuery(semester = "115-1", query = "數學")
            bridge.addFilter(department)
            bridge.pickSuggestion(department)
            bridge.setSchedule(setOf(monday1), SlotMatch.FITS_WITHIN)
            bridge.removeFilter(department)
            bridge.clearFilters()
            bridge.submit(semester = "115-1", query = "")

            assertEquals(
                listOf(
                    CourseSearchAction.UpdateQuery("115-1", "數學"),
                    CourseSearchAction.AddFilter(department),
                    CourseSearchAction.PickSuggestion(department),
                    CourseSearchAction.SetSchedule(setOf(monday1), SlotMatch.FITS_WITHIN),
                    CourseSearchAction.RemoveFilter(department),
                    CourseSearchAction.ClearFilters,
                    CourseSearchAction.Submit("115-1", ""),
                ),
                bridge.actions.take(7).toList(),
            )
        }
    }
}
