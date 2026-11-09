package org.mpc.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import org.mpc.data.local.dao.CourseCatalogDao
import org.mpc.data.local.dao.CoursePlanDao
import org.mpc.data.local.database.AppDatabase

@ContributesTo(AppScope::class)
@BindingContainer
class DatabaseProvider {
    @Provides
    fun providesCourseCatalogDao(database: AppDatabase): CourseCatalogDao = database.courseCatalogDao()

    @Provides
    fun providesCoursePlanDao(database: AppDatabase): CoursePlanDao = database.coursePlanDao()
}
