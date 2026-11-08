package org.mpc.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import org.mpc.data.local.database.AppDatabase
import org.mpc.domain.html.HtmlParser
import org.mpc.domain.repository.AppSettingsRepository
import org.mpc.domain.repository.CourseRepository
import org.mpc.domain.repository.EeclassCourseRepository
import org.mpc.domain.repository.PortalFavoritesRepository
import org.mpc.domain.session.CampusSessions
import org.mpc.presentation.state.CoursePlanDraftStore

@DependencyGraph(AppScope::class)
interface AppGraph : ViewModelGraph {
    val appSettingsRepository: AppSettingsRepository
    val courseRepository: CourseRepository
    val coursePlanDraftStore: CoursePlanDraftStore
    val campusSessions: CampusSessions
    val portalFavoritesRepository: PortalFavoritesRepository
    val eeclassCourseRepository: EeclassCourseRepository

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides dataStore: DataStore<Preferences>,
            @Provides database: AppDatabase,
            @Provides htmlParser: HtmlParser,
        ): AppGraph
    }
}

fun createAppGraph(
    dataStore: DataStore<Preferences>,
    database: AppDatabase,
    htmlParser: HtmlParser,
): AppGraph = createGraphFactory<AppGraph.Factory>().create(dataStore, database, htmlParser)
