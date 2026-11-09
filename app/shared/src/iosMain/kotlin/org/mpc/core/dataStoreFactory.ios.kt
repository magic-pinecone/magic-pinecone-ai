package org.mpc.core

import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import kotlinx.cinterop.ExperimentalForeignApi
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
fun createDataStore(): OkioStorage<Preferences> = OkioStorage(
    fileSystem = FileSystem.SYSTEM,
    serializer = PreferencesSerializer,
    producePath = {
        val applicationSupportPath =
            requireNotNull(
                NSFileManager.defaultManager.URLForDirectory(
                    directory = NSApplicationSupportDirectory,
                    inDomain = NSUserDomainMask,
                    appropriateForURL = null,
                    create = true,
                    error = null,
                ),
            )
        val filePath =
            requireNotNull(
                applicationSupportPath.URLByAppendingPathComponent(DATA_STORE_FILE_NAME),
            )

        requireNotNull(filePath.path).toPath()
    },
)
