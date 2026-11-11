package org.mpc.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import org.mpc.presentation.EeclassCategory
import org.mpc.presentation.model.InternationalPage

interface AndroidRoute : NavKey

@Serializable
data object AppRoot : AndroidRoute

@Serializable
data object SettingsRoute : AndroidRoute

@Serializable
sealed interface TopLevelRoute : AndroidRoute

@Serializable
data object HomeRoot : TopLevelRoute

@Serializable
data object PortalRoot : TopLevelRoute

@Serializable
data class PortalWebRoute(
    val title: String,
    val url: String,
) : AndroidRoute

@Serializable
data object ClubsRoute : AndroidRoute

/** A page for international students, opened from the home screen. */
@Serializable
data class InternationalRoute(
    val page: InternationalPage,
) : AndroidRoute

@Serializable
data class EeclassCourseRoute(
    val number: String,
    val name: String,
) : AndroidRoute

@Serializable
data class EeclassCategoryRoute(
    val number: String,
    val category: EeclassCategory,
) : AndroidRoute

@Serializable
data class EeclassBulletinRoute(
    val course: String,
    val number: String,
    val title: String,
) : AndroidRoute

@Serializable
data object CoursePlanningRoot : TopLevelRoute

val topLevelRoutes: Set<TopLevelRoute> =
    linkedSetOf(
        HomeRoot,
        CoursePlanningRoot,
        PortalRoot,
    )
