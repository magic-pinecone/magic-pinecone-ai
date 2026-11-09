package org.mpc.presentation.viewModel

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.mpc.domain.model.EeclassBulletinNumber
import org.mpc.domain.model.EeclassCourseNumber
import org.mpc.domain.repository.EeclassCourseRepository
import org.mpc.presentation.model.EeclassBulletinPage
import org.mpc.presentation.model.EeclassCoursePage
import org.mpc.presentation.model.EeclassCourses
import org.mpc.presentation.model.toEeclassBulletinPage
import org.mpc.presentation.model.toEeclassCoursePage
import org.mpc.presentation.model.toEeclassCourses

/**
 * Each navigation entry gets its own instance and uses one of the three loaders. A state stays null until its
 * first load finishes, and a later load keeps showing the previous result until the new one arrives.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(
    scope = AppScope::class,
    binding = binding<ViewModel>(),
)
class EeclassViewModel(
    private val repository: EeclassCourseRepository,
) : ViewModel() {
    private val _courses = MutableStateFlow<EeclassCourses?>(null)
    val courses: StateFlow<EeclassCourses?> = _courses

    private val _coursePage = MutableStateFlow<EeclassCoursePage?>(null)
    val coursePage: StateFlow<EeclassCoursePage?> = _coursePage

    private val _bulletin = MutableStateFlow<EeclassBulletinPage?>(null)
    val bulletin: StateFlow<EeclassBulletinPage?> = _bulletin

    suspend fun loadCourses() {
        _courses.value = repository.fetchEnrolledCourses().toEeclassCourses()
    }

    suspend fun loadCoursePage(course: String) {
        _coursePage.value = repository.fetchCourseContent(EeclassCourseNumber(course)).toEeclassCoursePage()
    }

    suspend fun loadBulletin(course: String, bulletin: String) {
        _bulletin.value = repository
            .fetchBulletin(EeclassCourseNumber(course), EeclassBulletinNumber(bulletin))
            .toEeclassBulletinPage()
    }
}
