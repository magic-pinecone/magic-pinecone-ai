package org.mpc.presentation.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.mpc.domain.model.AppSettings
import org.mpc.domain.model.AppThemeMode
import org.mpc.domain.model.CourseDay
import org.mpc.domain.model.CourseDetail
import org.mpc.domain.model.CoursePeriod
import org.mpc.domain.model.CoursePlan
import org.mpc.domain.model.CourseResult
import org.mpc.domain.model.CourseSerialNo
import org.mpc.domain.model.CourseSummary
import org.mpc.domain.model.CourseTime
import org.mpc.domain.model.CourseType
import org.mpc.domain.model.DistributionCondition
import org.mpc.domain.model.PasswordCardType
import org.mpc.domain.model.PortalAuthenticationMode
import org.mpc.domain.model.TimetableColorMode
import org.mpc.presentation.PortalScreen
import org.mpc.presentation.SettingsScreen
import org.mpc.presentation.state.CoursePlanUiState
import org.mpc.presentation.state.CourseSearchError
import org.mpc.presentation.state.CourseSearchResultUiState
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.presentation.views.courseDetails.CourseDetailsActions
import org.mpc.presentation.views.courseDetails.CourseDetailsUiState
import org.mpc.presentation.views.courseDetails.CourseDetailsView
import org.mpc.presentation.views.courseFilters.CourseSlotPicker
import org.mpc.presentation.views.coursePlanning.CoursePlanningTimetableView
import org.mpc.presentation.views.coursePlanning.CourseSearchResultView
import kotlin.time.Instant

enum class ComposePreviewScenario {
    CATALOG_POPULATED,
    CATALOG_EMPTY,
    CATALOG_LOADING,
    CATALOG_FAILURE,
    TIMETABLE_POPULATED,
    TIMETABLE_EMPTY,
    TIMETABLE_LOADING,
    TIMETABLE_FAILURE,
    COURSE_DETAILS,
    PORTAL,
    SETTINGS,
    SLOT_PICKER,
}

@Composable
internal fun ComposePreviewScenarioContent(scenario: ComposePreviewScenario) {
    when (scenario) {
        ComposePreviewScenario.CATALOG_POPULATED -> CourseSearchResultView(
            modifier = Modifier.fillMaxSize(),
            uiState = CourseSearchResultUiState.Success(ComposePreviewFixtures.courseResult),
            selectedCourseSerialNumbers = setOf(ComposePreviewFixtures.courses.first().serialNo),
            onToggleCourse = {},
        )

        ComposePreviewScenario.CATALOG_EMPTY -> CourseSearchResultView(
            modifier = Modifier.fillMaxSize(),
            uiState = CourseSearchResultUiState.Success(ComposePreviewFixtures.emptyCourseResult),
            selectedCourseSerialNumbers = emptySet(),
            onToggleCourse = {},
        )

        ComposePreviewScenario.CATALOG_LOADING -> CourseSearchResultView(
            modifier = Modifier.fillMaxSize(),
            uiState = CourseSearchResultUiState.Loading,
            selectedCourseSerialNumbers = emptySet(),
            onToggleCourse = {},
        )

        ComposePreviewScenario.CATALOG_FAILURE -> CourseSearchResultView(
            modifier = Modifier.fillMaxSize(),
            uiState = CourseSearchResultUiState.Failure(CourseSearchError.NETWORK),
            selectedCourseSerialNumbers = emptySet(),
            onToggleCourse = {},
        )

        ComposePreviewScenario.TIMETABLE_POPULATED -> CoursePlanningTimetableView(
            uiState = CoursePlanUiState.Success(ComposePreviewFixtures.coursePlan),
            modifier = Modifier.fillMaxSize(),
        )

        ComposePreviewScenario.TIMETABLE_EMPTY -> CoursePlanningTimetableView(
            uiState = CoursePlanUiState.Success(ComposePreviewFixtures.emptyCoursePlan),
            modifier = Modifier.fillMaxSize(),
        )

        ComposePreviewScenario.TIMETABLE_LOADING -> CoursePlanningTimetableView(
            uiState = CoursePlanUiState.Loading,
            modifier = Modifier.fillMaxSize(),
        )

        ComposePreviewScenario.TIMETABLE_FAILURE -> CoursePlanningTimetableView(
            uiState = CoursePlanUiState.Failure(IllegalStateException("課表暫時無法載入。")),
            modifier = Modifier.fillMaxSize(),
        )

        ComposePreviewScenario.COURSE_DETAILS -> CourseDetailsView(
            state = CourseDetailsUiState(
                summary = ComposePreviewFixtures.courses.first(),
                detail = ComposePreviewFixtures.courseDetail,
                isLoading = false,
                errorMessage = null,
                isSelected = true,
            ),
            actions = CourseDetailsActions(
                onToggleCourse = {},
                onRetry = {},
                onClose = {},
                onOpenOfficialPage = {},
            ),
            modifier = Modifier.fillMaxSize(),
        )

        ComposePreviewScenario.PORTAL -> PortalScreen(
            modifier = Modifier.fillMaxSize(),
            onOpenDestination = {},
        )

        ComposePreviewScenario.SETTINGS -> SettingsScreen(
            settings = ComposePreviewFixtures.settings,
            modifier = Modifier.fillMaxSize(),
            onThemeModeSelected = {},
            onPortalAuthenticationModeSelected = {},
            onTimetableColorModeSelected = {},
        )

        ComposePreviewScenario.SLOT_PICKER -> {
            var selected by remember { mutableStateOf(ComposePreviewFixtures.courses.first().classTimes.toSet()) }
            CourseSlotPicker(
                selected = selected,
                onSelectedChange = { selected = it },
                modifier = Modifier.padding(MpcSpacing.space4),
            )
        }
    }
}

internal object ComposePreviewFixtures {
    private const val SEMESTER = "1151"

    val courses = listOf(
        CourseSummary(
            serialNo = CourseSerialNo("51001"),
            classNo = "CSIE-5211",
            title = "計算機網路",
            credit = 3.0,
            passwordCard = PasswordCardType.NONE,
            teachers = listOf("陳教授"),
            classTimes = listOf(
                CourseTime(CourseDay.MONDAY, CoursePeriod.ONE),
                CourseTime(CourseDay.MONDAY, CoursePeriod.TWO),
                CourseTime(CourseDay.WEDNESDAY, CoursePeriod.THREE),
            ),
            limitCnt = 60,
            admitCnt = 58,
            waitCnt = 2,
            collegeName = "資訊電機學院",
            departmentName = "資訊工程學系",
            courseType = CourseType.REQUIRED,
            detailUrl = "https://course.ncu.edu.tw/",
        ),
        CourseSummary(
            serialNo = CourseSerialNo("51002"),
            classNo = "CSIE-3210",
            title = "資料庫系統",
            credit = 3.0,
            passwordCard = PasswordCardType.OPTIONAL,
            teachers = listOf("林教授"),
            classTimes = listOf(
                CourseTime(CourseDay.MONDAY, CoursePeriod.TWO),
                CourseTime(CourseDay.MONDAY, CoursePeriod.THREE),
                CourseTime(CourseDay.THURSDAY, CoursePeriod.SIX),
                CourseTime(CourseDay.THURSDAY, CoursePeriod.SEVEN),
            ),
            limitCnt = 55,
            admitCnt = 52,
            waitCnt = 4,
            collegeName = "資訊電機學院",
            departmentName = "資訊工程學系",
            courseType = CourseType.ELECTIVE,
            detailUrl = "https://course.ncu.edu.tw/",
        ),
        CourseSummary(
            serialNo = CourseSerialNo("51003"),
            classNo = "LN1-1040",
            title = "共同英文：學術溝通",
            credit = 2.0,
            passwordCard = PasswordCardType.NONE,
            teachers = listOf("王老師"),
            classTimes = listOf(
                CourseTime(CourseDay.FRIDAY, CoursePeriod.EIGHT),
                CourseTime(CourseDay.FRIDAY, CoursePeriod.NINE),
            ),
            limitCnt = 35,
            admitCnt = 32,
            waitCnt = 0,
            collegeName = "語言中心",
            departmentName = "共同教育",
            courseType = CourseType.REQUIRED,
            detailUrl = "https://course.ncu.edu.tw/",
        ),
    )

    val courseResult = CourseResult(
        lastUpdated = Instant.parse("2026-10-20T00:00:00Z"),
        semester = SEMESTER,
        courses = courses,
    )

    val emptyCourseResult = courseResult.copy(courses = emptyList())

    val coursePlan = CoursePlan(
        semester = SEMESTER,
        selectedCourses = courses.associateBy(CourseSummary::serialNo),
    )

    val emptyCoursePlan = CoursePlan(semester = SEMESTER, selectedCourses = emptyMap())

    val courseDetail = CourseDetail(
        serialNo = "51001",
        objectives = "理解網路分層架構、路由、傳輸控制與常見網路協定。",
        content = "從鏈路層到應用層介紹網路設計，並以封包分析與小型實作練習核心概念。",
        books = "Computer Networking: A Top-Down Approach",
        teachingMethod = "課堂講授、實作練習與案例討論。",
        gradingPolicy = "作業 30%、期中考 30%、期末專題 40%。",
        distributionConditions = listOf(
            DistributionCondition(priority = 1, rule = "資訊工程學系學生優先。"),
            DistributionCondition(priority = 2, rule = "依年級與志願序分發。"),
        ),
    )

    val settings = AppSettings(
        themeMode = AppThemeMode.LIGHT,
        portalAuthenticationMode = PortalAuthenticationMode.MANUAL_PORTAL,
        timetableColorMode = TimetableColorMode.CATEGORY,
    )
}
