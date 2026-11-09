package org.mpc.presentation.viewModel

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.mpc.domain.model.CourseDay
import org.mpc.domain.model.CourseDetail
import org.mpc.domain.model.CourseFilter
import org.mpc.domain.model.CoursePeriod
import org.mpc.domain.model.CourseQuery
import org.mpc.domain.model.CourseResult
import org.mpc.domain.model.CourseSerialNo
import org.mpc.domain.model.CourseSummary
import org.mpc.domain.model.CourseTime
import org.mpc.domain.model.CourseType
import org.mpc.domain.model.PasswordCardType
import org.mpc.domain.model.SeatAvailability
import org.mpc.domain.model.SlotMatch
import org.mpc.domain.repository.CourseRepository
import org.mpc.presentation.state.CourseFilterOptions
import org.mpc.presentation.state.CourseSearchError
import org.mpc.presentation.state.CourseSearchResultUiState
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

private val monday1 = CourseTime(CourseDay.MONDAY, CoursePeriod.ONE)
private val tuesday2 = CourseTime(CourseDay.TUESDAY, CoursePeriod.TWO)

private fun course(
    serialNo: String,
    title: String,
    teacher: String,
    departmentName: String,
    collegeName: String = "理學院",
    credit: Double = 3.0,
    classTimes: List<CourseTime> = emptyList(),
) = CourseSummary(
    serialNo = CourseSerialNo(serialNo),
    classNo = "01",
    title = title,
    credit = credit,
    passwordCard = PasswordCardType.NONE,
    teachers = listOf(teacher),
    classTimes = classTimes,
    limitCnt = 50,
    admitCnt = 0,
    waitCnt = 0,
    collegeName = collegeName,
    departmentName = departmentName,
    courseType = CourseType.REQUIRED,
    detailUrl = "",
)

private val catalog = listOf(
    course("10001", "微積分", "王明", "數學系", classTimes = listOf(monday1)),
    course("10002", "線性代數", "王明華", "應用數學系", credit = 2.0, classTimes = listOf(tuesday2)),
    course("10003", "中國文學史", "李大同", "中文系", collegeName = "文學院", credit = 2.0),
)

private class CatalogRepository(
    var failure: Throwable? = null,
) : CourseRepository {
    var fetches = 0

    override suspend fun fetchAllCourses(semester: String): CourseResult {
        fetches++
        failure?.let { throw it }
        return CourseResult(lastUpdated = Instant.fromEpochSeconds(0), semester = semester, courses = catalog)
    }

    override suspend fun searchCourses(query: CourseQuery): CourseResult = error("Search runs on the loaded catalog")

    override suspend fun fetchCoursesBySerialNo(
        semester: String,
        serialNos: List<CourseSerialNo>,
    ): CourseResult = error("Not used")

    override suspend fun fetchCourseDetail(
        semester: String,
        serialNo: String,
    ): CourseDetail = error("Not used")

    override fun observeCatalog(semester: String): Flow<CourseResult?> = error("Not used")

    override suspend fun refreshCatalog(semester: String): CourseResult = error("Not used")
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun viewModelTest(
    repository: CatalogRepository = CatalogRepository(),
    body: suspend TestScope.(CourseSearchViewModel, CatalogRepository) -> Unit,
) = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
        val viewModel = CourseSearchViewModel(repository)
        testScheduler.advanceUntilIdle()
        body(viewModel, repository)
    } finally {
        Dispatchers.resetMain()
    }
}

private fun CourseSearchViewModel.shownSerials(): List<String> {
    val result = uiState.value.result
    check(result is CourseSearchResultUiState.Success) { "Expected results, got $result" }
    return result.result.courses.map { it.serialNumber }
}

val courseSearchViewModelTests by testSuite {
    test("loading the catalog shows every course and the picker options") {
        viewModelTest { viewModel, _ ->
            assertEquals(listOf("10001", "10002", "10003"), viewModel.shownSerials())
            assertEquals(
                CourseFilterOptions(
                    teachers = listOf("李大同", "王明", "王明華").sorted(),
                    departments = listOf("中文系", "應用數學系", "數學系").sorted(),
                    colleges = listOf("文學院", "理學院").sorted(),
                    credits = listOf(2.0, 3.0),
                ),
                viewModel.uiState.value.filterOptions,
            )
        }
    }

    test("typing suggests filters without searching") {
        viewModelTest { viewModel, repository ->
            viewModel.updateQuery("115-1", "系所:數學")

            val state = viewModel.uiState.value
            assertEquals(listOf(CourseFilter.Department("數學系"), CourseFilter.Department("應用數學系")), state.suggestions)
            assertEquals(listOf("10001", "10002", "10003"), viewModel.shownSerials())
            assertEquals(1, repository.fetches)
        }
    }

    test("picking a suggestion adds one chip, clears the text, and narrows results") {
        viewModelTest { viewModel, repository ->
            viewModel.updateQuery("115-1", "數學")
            viewModel.pickSuggestion(CourseFilter.Department("數學系"))
            viewModel.pickSuggestion(CourseFilter.Department("數學系"))

            val state = viewModel.uiState.value
            assertEquals(listOf<CourseFilter>(CourseFilter.Department("數學系")), state.filters)
            assertEquals("", state.query)
            assertEquals(emptyList(), state.suggestions)
            assertEquals(listOf("10001"), viewModel.shownSerials())
            assertEquals(1, repository.fetches)
        }
    }

    test("adding a filter from the sheet keeps the typed keyword and searches with both") {
        viewModelTest { viewModel, _ ->
            viewModel.updateQuery("115-1", "線性")
            viewModel.addFilter(CourseFilter.Credits(2.0))

            val state = viewModel.uiState.value
            assertEquals("線性", state.query)
            assertEquals(listOf<CourseFilter>(CourseFilter.Credits(2.0)), state.filters)
            assertEquals(listOf("10002"), viewModel.shownSerials())
        }
    }

    test("active filters are not suggested again") {
        viewModelTest { viewModel, _ ->
            viewModel.addFilter(CourseFilter.Department("數學系"))
            viewModel.updateQuery("115-1", "系所:數學")
            assertEquals(listOf<CourseFilter>(CourseFilter.Department("應用數學系")), viewModel.uiState.value.suggestions)
        }
    }

    test("submitting combines the keyword with the chips") {
        viewModelTest { viewModel, _ ->
            viewModel.addFilter(CourseFilter.Credits(2.0))
            viewModel.updateQuery("115-1", "線性")
            viewModel.onSearch()
            assertEquals(listOf("10002"), viewModel.shownSerials())
        }
    }

    test("removing or clearing chips widens the results") {
        viewModelTest { viewModel, _ ->
            viewModel.addFilter(CourseFilter.Credits(2.0))
            viewModel.addFilter(CourseFilter.College("文學院"))
            assertEquals(listOf("10003"), viewModel.shownSerials())

            viewModel.removeFilter(CourseFilter.College("文學院"))
            assertEquals(listOf<CourseFilter>(CourseFilter.Credits(2.0)), viewModel.uiState.value.filters)
            assertEquals(listOf("10002", "10003"), viewModel.shownSerials())

            viewModel.clearFilters()
            assertEquals(emptyList(), viewModel.uiState.value.filters)
            assertEquals(listOf("10001", "10002", "10003"), viewModel.shownSerials())
        }
    }

    test("setting the schedule replaces its chip in place and empty slots remove it") {
        viewModelTest { viewModel, _ ->
            val open = CourseFilter.Seats(SeatAvailability.OPEN)
            viewModel.addFilter(open)
            viewModel.setSchedule(setOf(monday1), SlotMatch.FITS_WITHIN)
            viewModel.addFilter(CourseFilter.College("理學院"))
            assertEquals(listOf("10001"), viewModel.shownSerials())

            viewModel.setSchedule(setOf(monday1, tuesday2), SlotMatch.FITS_WITHIN)
            assertEquals(
                listOf(open, CourseFilter.Schedule(setOf(monday1, tuesday2), SlotMatch.FITS_WITHIN), CourseFilter.College("理學院")),
                viewModel.uiState.value.filters,
            )
            assertEquals(listOf("10001", "10002"), viewModel.shownSerials())

            viewModel.setSchedule(emptySet(), SlotMatch.FITS_WITHIN)
            assertEquals(listOf(open, CourseFilter.College("理學院")), viewModel.uiState.value.filters)
        }
    }

    test("an unsupported semester fails without fetching") {
        viewModelTest { viewModel, repository ->
            viewModel.updateQuery("114-2", "")
            viewModel.onSearch()
            assertEquals(
                CourseSearchResultUiState.Failure(CourseSearchError.INVALID_SEMESTER),
                viewModel.uiState.value.result,
            )
            assertEquals(1, repository.fetches)
        }
    }

    test("a failed catalog load reports failure and a retry recovers") {
        viewModelTest(CatalogRepository(failure = IllegalStateException("offline"))) { viewModel, repository ->
            assertEquals(CourseSearchResultUiState.Failure(CourseSearchError.UNKNOWN), viewModel.uiState.value.result)
            assertTrue(viewModel.uiState.value.filterOptions.teachers.isEmpty())

            repository.failure = null
            viewModel.onSearch()
            assertEquals(CourseSearchResultUiState.Loading, viewModel.uiState.value.result)
            testScheduler.advanceUntilIdle()
            assertEquals(listOf("10001", "10002", "10003"), viewModel.shownSerials())
            assertEquals(listOf("李大同", "王明", "王明華").sorted(), viewModel.uiState.value.filterOptions.teachers)
        }
    }
}
