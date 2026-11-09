package org.mpc.bridge

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.mpc.domain.model.CourseSummary

class CoursePlanBridge {
    private val requests = Channel<Unit>(Channel.BUFFERED)
    internal val saveRequests = requests.receiveAsFlow()
    private val courseToggleRequests = Channel<CourseSummary>(Channel.BUFFERED)
    internal val toggleRequests = courseToggleRequests.receiveAsFlow()
    private val mutablePlanSnapshot = MutableStateFlow(CoursePlanSnapshot())
    val planSnapshot: StateFlow<CoursePlanSnapshot> = mutablePlanSnapshot.asStateFlow()
    private var courseDetailsObserver: ((String, CourseSummary) -> Unit)? = null

    fun requestToggleCourse(course: CourseSummary) {
        courseToggleRequests.trySend(course)
    }

    fun observeCourseDetailsRequest(observer: ((String, CourseSummary) -> Unit)?) {
        courseDetailsObserver = observer
    }

    fun requestCourseDetails(semester: String, course: CourseSummary) {
        courseDetailsObserver?.invoke(semester, course)
    }

    fun updateSelectedCourses(courses: List<CourseSummary>, canEditPlan: Boolean) {
        mutablePlanSnapshot.value = CoursePlanSnapshot(
            selectedCourses = courses,
            canEditPlan = canEditPlan,
        )
    }

    fun requestSave() {
        requests.trySend(Unit)
    }
}

data class CoursePlanSnapshot(
    val selectedCourses: List<CourseSummary> = emptyList(),
    val canEditPlan: Boolean = false,
)
