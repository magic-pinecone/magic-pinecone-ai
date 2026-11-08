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
import org.mpc.domain.model.CourseDetail
import org.mpc.domain.model.CourseResult
import org.mpc.domain.model.CourseSerialNo
import org.mpc.domain.model.PortalAuthenticationMode
import org.mpc.domain.model.TimetableColorMode
import org.mpc.domain.repository.AppSettingsRepository
import org.mpc.domain.repository.CourseRepository
import org.mpc.domain.repository.EeclassCourseRepository
import org.mpc.domain.repository.PortalFavoritesRepository
import org.mpc.domain.session.CampusSessions
import org.mpc.presentation.state.CoursePlanDraftStore
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
) : AppGraph {
    override val courseRepository: CourseRepository get() = detailRepository ?: error("No course request expected")
    override val coursePlanDraftStore: CoursePlanDraftStore get() = error("No Compose controller expected")
    override val campusSessions: CampusSessions get() = error("No campus session expected")
    override val eeclassCourseRepository: EeclassCourseRepository get() = error("No ee-class request expected")
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

    override suspend fun fetchCourses(semester: String, query: String): CourseResult = error("No catalog request expected")

    override suspend fun fetchCoursesBySerialNo(semester: String, serialNos: List<CourseSerialNo>): CourseResult = error("No catalog request expected")

    override fun observeCatalog(semester: String): Flow<CourseResult?> = error("No catalog request expected")

    override suspend fun refreshCatalog(semester: String): CourseResult = error("No catalog request expected")
}
