package org.mpc.core

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import org.mpc.data.local.database.AppDatabase

fun createDatabaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase> {
    val appContext = context.applicationContext
    val databaseFile = appContext.getDatabasePath(DATABASE_NAME)

    return Room.databaseBuilder<AppDatabase>(
        context = appContext,
        name = databaseFile.absolutePath,
    )
}
