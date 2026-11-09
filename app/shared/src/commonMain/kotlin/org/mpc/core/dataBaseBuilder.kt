package org.mpc.core

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.mpc.data.local.database.AppDatabase

// This is the database composition root, before the Metro graph exists.
@Suppress("InjectDispatcher")
fun createDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase = builder
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()

internal const val DATABASE_NAME = "mpc.db"
