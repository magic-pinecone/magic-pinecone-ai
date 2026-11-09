package org.mpc.presentation.viewModel

import de.infix.testBalloon.framework.core.testSuite
import dev.icerock.moko.resources.desc.PluralFormatted
import dev.icerock.moko.resources.desc.Resource
import dev.icerock.moko.resources.desc.StringDesc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.mpc.domain.model.CourseDetail
import org.mpc.domain.model.CourseQuery
import org.mpc.domain.model.CourseResult
import org.mpc.domain.model.CourseSerialNo
import org.mpc.domain.repository.BackendEndpointRepository
import org.mpc.domain.repository.CourseRepository
import org.mpc.presentation.state.BackendEndpointUiState
import org.mpc.presentation.state.BackendSyncError
import org.mpc.presentation.state.BackendSyncState
import org.mpc.presentation.state.message
import org.mpc.shared.MR
import org.mpc.shared.settings_invalid_address
import org.mpc.shared.settings_source_unavailable
import org.mpc.shared.settings_synced
import org.mpc.shared.settings_syncing
import kotlin.test.assertEquals
import kotlin.time.Instant

private const val OLD_BACKEND = "http://old.example.edu"
private const val NEW_BACKEND = "http://10.0.2.2:8000"

private class StoredBackendEndpoint(
    baseUrl: String?,
) : BackendEndpointRepository {
    override val baseUrl = MutableStateFlow(baseUrl)

    override suspend fun setBaseUrl(baseUrl: String?) {
        this.baseUrl.value = baseUrl
    }
}

/** Records which source was configured at the moment each catalog refresh ran. */
private class RefreshingRepository(
    private val endpoint: StoredBackendEndpoint,
    var failure: Throwable? = null,
) : CourseRepository {
    val refreshedFrom = mutableListOf<String?>()

    override suspend fun refreshCatalog(semester: String): CourseResult {
        refreshedFrom += endpoint.baseUrl.value
        failure?.let { throw it }
        return CourseResult(lastUpdated = Instant.fromEpochSeconds(0), semester = semester, courses = emptyList())
    }

    override suspend fun fetchAllCourses(semester: String): CourseResult = error("Not used")

    override suspend fun searchCourses(query: CourseQuery): CourseResult = error("Not used")

    override suspend fun fetchCoursesBySerialNo(
        semester: String,
        serialNos: List<CourseSerialNo>,
    ): CourseResult = error("Not used")

    override suspend fun fetchCourseDetail(
        semester: String,
        serialNo: String,
    ): CourseDetail = error("Not used")

    override fun observeCatalog(semester: String): Flow<CourseResult?> = error("Not used")
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun backendEndpointTest(
    stored: String? = OLD_BACKEND,
    failure: Throwable? = null,
    body: suspend TestScope.(BackendEndpointViewModel, RefreshingRepository) -> Unit,
) = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
        val endpoint = StoredBackendEndpoint(stored)
        val repository = RefreshingRepository(endpoint, failure)
        val viewModel = BackendEndpointViewModel(endpoint, repository)
        testScheduler.advanceUntilIdle()
        body(viewModel, repository)
    } finally {
        Dispatchers.resetMain()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
val backendEndpointViewModelTests by testSuite {
    test("sync states expose localized messages") {
        assertEquals(null, BackendSyncState.Idle.message())
        assertEquals(StringDesc.Resource(MR.strings.settings_syncing), BackendSyncState.Syncing.message())
        assertEquals(
            StringDesc.PluralFormatted(MR.plurals.settings_synced, 2, 2),
            BackendSyncState.Synced(courseCount = 2).message(),
        )
        assertEquals(
            StringDesc.Resource(MR.strings.settings_invalid_address),
            BackendSyncState.Failed(BackendSyncError.INVALID_ADDRESS).message(),
        )
        assertEquals(
            StringDesc.Resource(MR.strings.settings_source_unavailable),
            BackendSyncState.Failed(BackendSyncError.SOURCE_UNAVAILABLE).message(),
        )
    }

    test("the stored backend is shown before anything is synced") {
        backendEndpointTest { viewModel, repository ->
            assertEquals(BackendEndpointUiState(baseUrl = OLD_BACKEND), viewModel.uiState.value)
            assertEquals(emptyList(), repository.refreshedFrom)
        }
    }

    test("using a backend stores its address and reloads the catalog from it") {
        backendEndpointTest { viewModel, repository ->
            viewModel.useSource(" $NEW_BACKEND/ ")
            testScheduler.advanceUntilIdle()

            assertEquals(listOf<String?>(NEW_BACKEND), repository.refreshedFrom)
            assertEquals(
                BackendEndpointUiState(baseUrl = NEW_BACKEND, sync = BackendSyncState.Synced(courseCount = 0)),
                viewModel.uiState.value,
            )
        }
    }

    test("a blank address goes back to the static catalog") {
        backendEndpointTest { viewModel, repository ->
            viewModel.useSource("  ")
            testScheduler.advanceUntilIdle()

            assertEquals(listOf<String?>(null), repository.refreshedFrom)
            assertEquals(null, viewModel.uiState.value.baseUrl)
        }
    }

    test("a backend that cannot deliver the catalog is not kept") {
        backendEndpointTest(failure = IllegalStateException("connection refused")) { viewModel, repository ->
            viewModel.useSource(NEW_BACKEND)
            testScheduler.advanceUntilIdle()

            assertEquals(listOf<String?>(NEW_BACKEND), repository.refreshedFrom)
            assertEquals(
                BackendEndpointUiState(
                    baseUrl = OLD_BACKEND,
                    sync = BackendSyncState.Failed(BackendSyncError.SOURCE_UNAVAILABLE),
                ),
                viewModel.uiState.value,
            )
        }
    }

    test("an address that is not http is rejected without touching the catalog") {
        backendEndpointTest { viewModel, repository ->
            viewModel.useSource("localhost:8000")
            testScheduler.advanceUntilIdle()

            assertEquals(emptyList(), repository.refreshedFrom)
            assertEquals(
                BackendEndpointUiState(
                    baseUrl = OLD_BACKEND,
                    sync = BackendSyncState.Failed(BackendSyncError.INVALID_ADDRESS),
                ),
                viewModel.uiState.value,
            )
        }
    }
}
