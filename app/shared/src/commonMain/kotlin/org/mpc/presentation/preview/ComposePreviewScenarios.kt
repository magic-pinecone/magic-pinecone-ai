package org.mpc.presentation.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.mpc.domain.model.AppLanguage
import org.mpc.domain.model.AppSettings
import org.mpc.domain.model.AppThemeMode
import org.mpc.domain.model.Club
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
import org.mpc.domain.model.GovGuideArticle
import org.mpc.domain.model.GovGuides
import org.mpc.domain.model.LocalizedText
import org.mpc.domain.model.PasswordCardType
import org.mpc.domain.model.PortalAuthenticationMode
import org.mpc.domain.model.RuleContact
import org.mpc.domain.model.RuleDegree
import org.mpc.domain.model.RuleSource
import org.mpc.domain.model.SourcedRule
import org.mpc.domain.model.SourcedRules
import org.mpc.domain.model.TimetableColorMode
import org.mpc.domain.model.collegesFor
import org.mpc.domain.model.rulesFor
import org.mpc.presentation.ClubsScreen
import org.mpc.presentation.ClubsView
import org.mpc.presentation.GovGuidesRoute
import org.mpc.presentation.GovGuidesScreen
import org.mpc.presentation.GraduationLanguageActions
import org.mpc.presentation.GraduationLanguageScreen
import org.mpc.presentation.PortalScreen
import org.mpc.presentation.ServiceLearningRoute
import org.mpc.presentation.ServiceLearningScreen
import org.mpc.presentation.SettingsScreen
import org.mpc.presentation.state.ClubUiState
import org.mpc.presentation.state.CoursePlanUiState
import org.mpc.presentation.state.CourseSearchError
import org.mpc.presentation.state.CourseSearchResultUiState
import org.mpc.presentation.state.FIRST_COHORT
import org.mpc.presentation.state.GovGuidesUiState
import org.mpc.presentation.state.GraduationLanguageFilter
import org.mpc.presentation.state.GraduationLanguageUiState
import org.mpc.presentation.state.LAST_COHORT
import org.mpc.presentation.state.SourcedRulesUiState
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.presentation.viewModel.GraduationLanguageViewModel
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
    GUIDES_POPULATED,

    /** Reads the running backend through [org.mpc.presentation.viewModel.GovGuidesViewModel]. */
    GUIDES_LIVE,

    SERVICE_LEARNING_POPULATED,

    /** Reads the running backend through [org.mpc.presentation.viewModel.ServiceLearningViewModel]. */
    SERVICE_LEARNING_LIVE,

    CLUBS_POPULATED,
    CLUBS_NO_BACKEND,
    CLUBS_FAILURE,

    /** Reads the running backend through [org.mpc.presentation.viewModel.ClubViewModel]. */
    CLUBS_LIVE,

    /** Shows [GraduationLanguagePreviewFixture], an excerpt of the backend's data. */
    GRADUATION_POPULATED,
    GRADUATION_LOADING,
    GRADUATION_FAILURE,
    GRADUATION_NEEDSBACKEND,

    /** Reads the running backend through [GraduationLanguageViewModel]. */
    GRADUATION_LIVE,
}

// One branch per scenario; splitting the dispatch would only scatter it.
@Suppress("CyclomaticComplexMethod")
@Composable
internal fun ComposePreviewScenarioContent(
    scenario: ComposePreviewScenario,
    language: AppLanguage = AppLanguage.SYSTEM,
    onLanguageSelected: (AppLanguage) -> Unit = {},
) {
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
            settings = ComposePreviewFixtures.settings.copy(language = language),
            modifier = Modifier.fillMaxSize(),
            onLanguageSelected = onLanguageSelected,
            onThemeModeSelected = {},
            onPortalAuthenticationModeSelected = {},
            onTimetableColorModeSelected = {},
        )

        ComposePreviewScenario.GUIDES_POPULATED -> GovGuidesScreen(
            state = GovGuidesUiState.Loaded(ComposePreviewFixtures.govGuides),
            onRetry = {},
            modifier = Modifier.fillMaxSize(),
        )

        ComposePreviewScenario.GUIDES_LIVE -> GovGuidesRoute(modifier = Modifier.fillMaxSize())

        ComposePreviewScenario.SERVICE_LEARNING_POPULATED -> ServiceLearningScreen(
            state = SourcedRulesUiState.Loaded(ComposePreviewFixtures.serviceLearning),
            onRetry = {},
            modifier = Modifier.fillMaxSize(),
        )

        ComposePreviewScenario.SERVICE_LEARNING_LIVE -> ServiceLearningRoute(modifier = Modifier.fillMaxSize())

        ComposePreviewScenario.CLUBS_POPULATED -> ClubsView(
            state = ClubUiState.Loaded(ComposePreviewFixtures.clubs.groupBy { it.categoryName }.toList()),
            onRetry = {},
            modifier = Modifier.fillMaxSize(),
        )

        ComposePreviewScenario.CLUBS_NO_BACKEND -> ClubsView(
            state = ClubUiState.NoBackend,
            onRetry = {},
            modifier = Modifier.fillMaxSize(),
        )

        ComposePreviewScenario.CLUBS_FAILURE -> ClubsView(
            state = ClubUiState.Failed,
            onRetry = {},
            modifier = Modifier.fillMaxSize(),
        )

        ComposePreviewScenario.CLUBS_LIVE -> ClubsScreen(modifier = Modifier.fillMaxSize())

        ComposePreviewScenario.SLOT_PICKER -> {
            var selected by remember { mutableStateOf(ComposePreviewFixtures.courses.first().classTimes.toSet()) }
            CourseSlotPicker(
                selected = selected,
                onSelectedChange = { selected = it },
                modifier = Modifier.padding(MpcSpacing.space4),
            )
        }

        ComposePreviewScenario.GRADUATION_POPULATED,
        ComposePreviewScenario.GRADUATION_LOADING,
        ComposePreviewScenario.GRADUATION_FAILURE,
        ComposePreviewScenario.GRADUATION_NEEDSBACKEND,
        -> GraduationLanguagePreview(scenario)

        ComposePreviewScenario.GRADUATION_LIVE -> GraduationLanguageLive()
    }
}

/** The screen as the app's International tab runs it, on whatever the preview host's backend answers. */
@Composable
private fun GraduationLanguageLive() {
    val viewModel: GraduationLanguageViewModel = metroViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GraduationLanguageScreen(
        state = state,
        actions = remember(viewModel) {
            GraduationLanguageActions(
                onSelectDegree = viewModel::selectDegree,
                onSelectCollege = viewModel::selectCollege,
                onSelectCohort = viewModel::selectCohort,
                onRetry = viewModel::refresh,
            )
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * The screen in the state [scenario] names. The populated one shows the bundled excerpt with working filters,
 * narrowed the way [GraduationLanguageViewModel] narrows them.
 */
@Composable
private fun GraduationLanguagePreview(scenario: ComposePreviewScenario) {
    val requirements = GraduationLanguagePreviewFixture.requirements
    var filter by remember { mutableStateOf(GraduationLanguageFilter()) }
    GraduationLanguageScreen(
        state = when (scenario) {
            ComposePreviewScenario.GRADUATION_FAILURE -> GraduationLanguageUiState.Failed

            ComposePreviewScenario.GRADUATION_NEEDSBACKEND -> GraduationLanguageUiState.NeedsBackend

            ComposePreviewScenario.GRADUATION_POPULATED -> GraduationLanguageUiState.Loaded(
                requirements = requirements,
                filter = filter,
                colleges = requirements.collegesFor(filter.degree),
                rules = requirements.rulesFor(filter.degree, filter.college, filter.cohort),
            )

            else -> GraduationLanguageUiState.Loading
        },
        actions = GraduationLanguageActions(
            onSelectDegree = { filter = filter.copy(degree = it, college = null) },
            onSelectCollege = { filter = filter.copy(college = it) },
            onSelectCohort = { filter = filter.copy(cohort = it.coerceIn(FIRST_COHORT, LAST_COHORT)) },
            onRetry = {},
        ),
        modifier = Modifier.fillMaxSize(),
    )
}

internal object ComposePreviewFixtures {
    // Shortened from the backend's gov_guides corpus.
    val govGuides = GovGuides(
        topics = listOf("work", "insurance", "residence", "help"),
        articles = listOf(
            GovGuideArticle(
                id = "wda-summer-hours",
                title = "僑外生申請工作許可，寒暑打工不限每週20小時",
                org = "勞動部勞動力發展署",
                lang = "zh",
                topic = "work",
                url = "https://www.wda.gov.tw/News_Content.aspx?n=31&s=3868",
                fetchedOn = "2026-11-10",
                body = "僑外生包括來臺就學僑生、港澳生及外國學生，求學期間可依規定申請工作許可，利用課餘時間工作補貼在臺生活，每星期工作時數最長為20個小時，但寒暑假期間不受此限。\n" +
                    "發展署提醒，僑外生如未經許可而自行從事工作，依法可處最高新臺幣15萬元罰鍰。",
            ),
            GovGuideArticle(
                id = "ocac-work-permit",
                title = "在學僑生如何申請工作證",
                org = "僑務委員會",
                lang = "zh",
                topic = "work",
                url = "https://www.ocac.gov.tw/OCAC/Pages/Detail.aspx?nodeid=4512&pid=13289460",
                fetchedOn = "2026-11-10",
                body = "◆申請流程及注意事項：\n工作證之申請須7-10個工作天，另許可期間原則上最長1年。",
            ),
            GovGuideArticle(
                id = "wda-ezwp",
                title = "外國專業人員工作許可申辦網",
                org = "勞動部勞動力發展署",
                lang = "zh",
                topic = "work",
                url = "https://ezwp.wda.gov.tw",
                isService = true,
            ),
            GovGuideArticle(
                id = "nhi-students",
                title = "在臺僑生、外籍生參加全民健康保險須知",
                org = "衛生福利部中央健康保險署",
                lang = "zh",
                topic = "insurance",
                url = "https://www.nhi.gov.tw/ch/dl-72718-2f56651c5a814b4cae4b8176333914e7-1.pdf",
                fetchedOn = "2026-11-10",
                body = "僑外生就學期間：自在臺居留滿 6 個月之日投保，以就讀學校為投保單位。\n" +
                    "僑外生經主管機關許可工作期間：自受僱當日投保（無等待期），以工作單位為投保單位。",
            ),
            GovGuideArticle(
                id = "nia-residence-tips",
                title = "What a foreigner should know during residence in Taiwan",
                org = "National Immigration Agency",
                lang = "en",
                topic = "residence",
                url = "https://servicestation.immigration.gov.tw/",
                fetchedOn = "2026-11-10",
                body = "1. Extension for Alien Resident Certificate (ARC): within 30 days prior to the expiration date of your residence.\n" +
                    "4. Changing address: apply for modification of your resident address within 15 days.",
            ),
            GovGuideArticle(
                id = "nia-hotline",
                title = "業務諮詢一覽表（1990 外來人士在臺生活諮詢服務熱線）",
                org = "內政部移民署",
                lang = "zh",
                topic = "help",
                url = "https://www.immigration.gov.tw/5385/5388/7181/100924/",
                fetchedOn = "2026-11-10",
                body = "外來人士在臺生活諮詢服務熱線 | 1990\n中、英、日語：24小時、全年無休。",
            ),
        ),
    )

    private val serviceLearningSource = RuleSource(
        url = "https://service-learning.ncu.edu.tw/",
        title = "學務處服務學習發展中心",
        fetched = "2026-11-10",
        quote = "服務學習課程為零學分必修，修習兩學期。",
    )

    val serviceLearning = SourcedRules(
        contact = RuleContact(
            name = LocalizedText("學務處服務學習發展中心", "Center for Service Learning, Office of Student Affairs"),
            website = "https://service-learning.ncu.edu.tw/",
            sources = listOf(serviceLearningSource),
        ),
        rules = listOf(
            SourcedRule(
                id = "course",
                degrees = listOf(RuleDegree.BACHELOR),
                title = LocalizedText(
                    "服務學習課程：零學分必修，修兩學期",
                    "Service-learning course: required, zero credits, two semesters",
                ),
                summary = LocalizedText(
                    "學士班學生須修習服務學習課程兩學期，不計學分，但列為畢業條件。",
                    "Bachelor's students take the course for two semesters. It carries no credits but counts toward graduation.",
                ),
                sources = listOf(serviceLearningSource),
            ),
            SourcedRule(
                id = "graduate",
                degrees = listOf(RuleDegree.GRADUATE),
                title = LocalizedText("研究所的服務學習", "Service learning for graduate students"),
                summary = LocalizedText("以官方公告為準。", "Follow the official announcement."),
                sources = listOf(serviceLearningSource),
            ),
        ),
        openQuestions = listOf("外籍生是否可以用其他活動折抵？"),
    )

    val clubs = listOf(
        Club(
            id = 1049,
            category = "A",
            categoryName = "聯誼性",
            name = "桃園地區校友會",
            nameEn = "NCUTY",
            description = "我們是中央的桃園地區校友會~~\n歡迎大家加入我們的行列喔!",
            address = "據德樓",
            website = "",
            pictureUrl = "",
        ),
        Club(
            id = 1051,
            category = "A",
            categoryName = "聯誼性",
            name = "新竹校友會",
            nameEn = "NCUHsinChu",
            description = "我們是來自大竹苗地區的學長姐",
            address = "夢工廠",
            website = "https://www.facebook.com/NCUHsinChu/",
            pictureUrl = "",
        ),
        Club(
            id = 1171,
            category = "G",
            categoryName = "學生自治組織",
            name = "學生會",
            nameEn = "Student Association",
            description = "",
            address = "",
            website = "",
            pictureUrl = "",
        ),
    )

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
