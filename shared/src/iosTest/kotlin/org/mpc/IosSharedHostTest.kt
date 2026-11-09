package org.mpc

import androidx.lifecycle.ViewModel
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.mpc.di.AppGraph
import org.mpc.domain.model.AppSettings
import org.mpc.domain.model.AppThemeMode
import org.mpc.domain.model.CourseDay
import org.mpc.domain.model.CourseDetail
import org.mpc.domain.model.CoursePeriod
import org.mpc.domain.model.CoursePlan
import org.mpc.domain.model.CourseQuery
import org.mpc.domain.model.CourseResult
import org.mpc.domain.model.CourseSerialNo
import org.mpc.domain.model.CourseSummary
import org.mpc.domain.model.CourseTime
import org.mpc.domain.model.CourseType
import org.mpc.domain.model.PasswordCardType
import org.mpc.domain.model.PortalAuthenticationMode
import org.mpc.domain.model.TimetableColorMode
import org.mpc.domain.repository.AcademicTermRepository
import org.mpc.domain.repository.AppSettingsRepository
import org.mpc.domain.repository.CoursePlanRepository
import org.mpc.domain.repository.CourseRepository
import org.mpc.domain.repository.EeclassCourseRepository
import org.mpc.domain.repository.PortalFavoritesRepository
import org.mpc.domain.session.CampusSessions
import org.mpc.presentation.state.CoursePlanDraftStore
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

val iosSharedHostTests by testSuite {
    test("cancelling course details cancels the repository request") {
        runTest {
            val request = CompletableDeferred<Pair<String, String>>()
            val cancelled = CompletableDeferred<Unit>()
            val repository = SuspendedDetailRepository { semester, serialNumber ->
                request.complete(semester to serialNumber)
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
            val host = IosSharedHost(HostTestGraph(HostSettingsRepository(), repository), settingsScope = backgroundScope)
            val operation = launch(start = CoroutineStart.UNDISPATCHED) {
                host.loadCourseDetail("115-1", "01001")
            }

            assertEquals("115-1" to "01001", request.await())
            assertFalse(cancelled.isCompleted)
            operation.cancelAndJoin()
            assertTrue(operation.isCancelled)
            assertTrue(cancelled.isCompleted)
        }
    }

    test("intent APIs search the catalog and read the saved plan") {
        runTest {
            val courses = listOf(intentCourse("10001", "微積分"), intentCourse("10002", "線性代數"))
            val catalog = CatalogRepository(courses)
            val plan = CoursePlan("115-1", courses.take(1).associateBy { it.serialNo })
            val host = IosSharedHost(HostTestGraph(HostSettingsRepository(), catalog, FixedPlanRepository(plan)), settingsScope = backgroundScope)

            assertEquals(listOf("10001"), host.findCourses("115-1", "", emptyList(), limit = 1).map { it.serialNumber })
            assertEquals("10002", host.course("115-1", "10002")?.serialNumber)
            assertEquals(listOf("數學系"), host.courseFilterOptions("115-1").departments)
            assertEquals(null, host.course("115-1", "bad"))
            assertEquals(listOf("10001"), host.savedMeetingsOn("115-1", CourseDay.MONDAY).map { it.course.serialNumber })
            assertEquals(480, host.nextSavedMeeting("115-1", CourseDay.TUESDAY, 0)?.meeting?.startMinute)
            assertEquals(1, host.nextSavedMeeting("115-1", CourseDay.SUNDAY, 0)?.daysFromToday)
        }
    }

    test("theme waits for preferences and updates without a Compose controller") {
        runTest {
            val repository = HostSettingsRepository()
            val host = IosSharedHost(HostTestGraph(repository), settingsScope = backgroundScope)
            val received = mutableListOf<AppThemeMode>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                host.themeMode.collect { received.add(it) }
            }
            testScheduler.runCurrent()
            assertTrue(received.isEmpty())

            repository.settings.emit(AppSettings(themeMode = AppThemeMode.DARK))
            testScheduler.runCurrent()
            repository.settings.emit(AppSettings(themeMode = AppThemeMode.DARK, timetableColorMode = TimetableColorMode.SHUFFLED))
            testScheduler.runCurrent()
            repository.settings.emit(AppSettings(themeMode = AppThemeMode.LIGHT))
            testScheduler.runCurrent()

            assertEquals(listOf(AppThemeMode.DARK, AppThemeMode.LIGHT), received)
            collector.cancelAndJoin()
        }
    }

    test("theme replays the latest preferences after collection restarts") {
        runTest {
            val repository = HostSettingsRepository()
            val host = IosSharedHost(HostTestGraph(repository), settingsScope = backgroundScope)
            val received = mutableListOf<AppThemeMode>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                host.themeMode.collect { received.add(it) }
            }
            repository.settings.emit(AppSettings(themeMode = AppThemeMode.DARK))
            testScheduler.runCurrent()
            collector.cancelAndJoin()
            repository.settings.emit(AppSettings(themeMode = AppThemeMode.LIGHT))
            testScheduler.runCurrent()
            assertEquals(listOf(AppThemeMode.DARK), received)

            val restartedCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                host.themeMode.collect { received.add(it) }
            }
            assertEquals(listOf(AppThemeMode.DARK, AppThemeMode.LIGHT), received)
            restartedCollector.cancelAndJoin()
        }
    }
}

private class HostSettingsRepository : AppSettingsRepository {
    override val settings = MutableSharedFlow<AppSettings>(replay = 1)

    override suspend fun setThemeMode(themeMode: AppThemeMode): Unit = error("Settings writes are unused by the host")

    override suspend fun setPortalAuthenticationMode(authenticationMode: PortalAuthenticationMode): Unit = error("Settings writes are unused by the host")

    override suspend fun setTimetableColorMode(colorMode: TimetableColorMode): Unit = error("Settings writes are unused by the host")
}

private class HostTestGraph(
    override val appSettingsRepository: AppSettingsRepository,
    private val detailRepository: CourseRepository? = null,
    private val planRepository: CoursePlanRepository? = null,
) : AppGraph {
    override val coursePlanRepository: CoursePlanRepository get() = planRepository ?: error("No plan request expected")
    override val courseRepository: CourseRepository get() = detailRepository ?: error("No course request expected")
    override val coursePlanDraftStore: CoursePlanDraftStore get() = error("No Compose controller expected")
    override val campusSessions: CampusSessions get() = error("No campus session expected")
    override val eeclassCourseRepository: EeclassCourseRepository get() = error("No ee-class request expected")
    override val academicTermRepository: AcademicTermRepository get() = error("No academic term expected")
    override val portalFavoritesRepository: PortalFavoritesRepository get() = error("No Portal favorites expected")
    override val metroViewModelFactory: MetroViewModelFactory get() = error("No Compose controller expected")
    override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel> = emptyMap()
    override val assistedFactoryProviders: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory> = emptyMap()
    override val manualAssistedFactoryProviders: Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory> = emptyMap()
}

private class SuspendedDetailRepository(
    private val fetchDetail: suspend (String, String) -> CourseDetail,
) : CourseRepository {
    override suspend fun fetchCourseDetail(semester: String, serialNo: String): CourseDetail = fetchDetail(semester, serialNo)

    override suspend fun fetchAllCourses(semester: String): CourseResult = error("No catalog request expected")

    override suspend fun searchCourses(query: CourseQuery): CourseResult = error("No catalog request expected")

    override suspend fun fetchCoursesBySerialNo(semester: String, serialNos: List<CourseSerialNo>): CourseResult = error("No catalog request expected")

    override fun observeCatalog(semester: String): Flow<CourseResult?> = error("No catalog request expected")

    override suspend fun refreshCatalog(semester: String): CourseResult = error("No catalog request expected")
}

private fun intentCourse(serialNo: String, title: String) = CourseSummary(
    serialNo = CourseSerialNo(serialNo),
    classNo = "01",
    title = title,
    credit = 3.0,
    passwordCard = PasswordCardType.NONE,
    teachers = emptyList(),
    classTimes = listOf(CourseTime(CourseDay.MONDAY, CoursePeriod.ONE)),
    limitCnt = 50,
    admitCnt = 0,
    waitCnt = 0,
    collegeName = "理學院",
    departmentName = "數學系",
    courseType = CourseType.REQUIRED,
    detailUrl = "",
)

private class FixedPlanRepository(private val plan: CoursePlan) : CoursePlanRepository {
    override suspend fun loadPlan(semester: String): CoursePlan = plan

    override suspend fun savePlan(plan: CoursePlan): Unit = error("No plan write expected")

    override suspend fun deletePlan(semester: String): Unit = error("No plan write expected")
}

private class CatalogRepository(private val courses: List<CourseSummary>) : CourseRepository {
    private fun result() = CourseResult(Instant.fromEpochSeconds(0), "115-1", courses)

    override suspend fun searchCourses(query: CourseQuery): CourseResult = result()

    override suspend fun fetchCoursesBySerialNo(semester: String, serialNos: List<CourseSerialNo>): CourseResult = CourseResult(Instant.fromEpochSeconds(0), semester, courses.filter { it.serialNo in serialNos })

    override suspend fun fetchCourseDetail(semester: String, serialNo: String): CourseDetail = error("No detail request expected")

    override suspend fun fetchAllCourses(semester: String): CourseResult = error("No catalog request expected")

    override fun observeCatalog(semester: String): Flow<CourseResult?> = error("No catalog request expected")

    override suspend fun refreshCatalog(semester: String): CourseResult = error("No catalog request expected")
}
