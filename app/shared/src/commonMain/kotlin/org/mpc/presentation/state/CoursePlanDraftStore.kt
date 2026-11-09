package org.mpc.presentation.state

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.mpc.domain.model.CoursePlan
import org.mpc.domain.model.CourseSummary
import org.mpc.domain.model.addCourse
import org.mpc.domain.model.removeCourse

@Inject
@SingleIn(AppScope::class)
class CoursePlanDraftStore {
    private val _uiState = MutableStateFlow<CoursePlanUiState>(CoursePlanUiState.Loading)

    val uiState = _uiState.asStateFlow()

    fun shouldLoad(semester: String): Boolean {
        val currentState = uiState.value
        return currentState !is CoursePlanUiState.Success ||
            currentState.plan.semester != semester
    }

    fun acceptLoadedPlan(plan: CoursePlan) {
        _uiState.update { currentState ->
            if (
                currentState is CoursePlanUiState.Success &&
                currentState.plan.semester == plan.semester
            ) {
                currentState
            } else {
                CoursePlanUiState.Success(plan)
            }
        }
    }

    fun acceptLoadFailure(cause: Throwable) {
        _uiState.update { currentState ->
            if (currentState is CoursePlanUiState.Loading) {
                CoursePlanUiState.Failure(cause)
            } else {
                currentState
            }
        }
    }

    fun toggleCourse(course: CourseSummary) {
        _uiState.update { currentState ->
            if (currentState !is CoursePlanUiState.Success) {
                return@update currentState
            }

            val updatedPlan =
                if (
                    currentState.plan.selectedCourses.containsKey(course.serialNo)
                ) {
                    currentState.plan.removeCourse(course)
                } else {
                    currentState.plan.addCourse(course)
                }

            CoursePlanUiState.Success(updatedPlan)
        }
    }
}
