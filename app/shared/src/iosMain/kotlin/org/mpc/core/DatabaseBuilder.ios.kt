package org.mpc.core

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import org.mpc.data.local.database.AppDatabase
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
fun createDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val applicationSupportPath =
        requireNotNull(
            NSFileManager.defaultManager.URLForDirectory(
                NSApplicationSupportDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = true,
                error = null,
            ),
        )
    val databasePath =
        requireNotNull(
            applicationSupportPath.URLByAppendingPathComponent(DATABASE_NAME)?.path,
        )

    return Room.databaseBuilder(
        name = databasePath,
    )
}
