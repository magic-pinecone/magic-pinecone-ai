package org.mpc.data.local.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import org.mpc.data.local.dao.CourseCatalogDao
import org.mpc.data.local.dao.CoursePlanDao

@Database(
    entities = [
        CourseCatalogEntity::class,
        CourseEntity::class,
        CourseTimeEntity::class,
        CourseTeacherEntity::class,
        CoursePlanEntity::class,
        CoursePlanItemEntity::class,
    ],
    version = 1,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courseCatalogDao(): CourseCatalogDao

    abstract fun coursePlanDao(): CoursePlanDao
}

expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
