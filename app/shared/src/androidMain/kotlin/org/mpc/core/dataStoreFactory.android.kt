package org.mpc.core

import android.content.Context
import androidx.datastore.core.Storage
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import okio.FileSystem
import okio.Path.Companion.toOkioPath

fun createDataStore(context: Context): Storage<Preferences> = OkioStorage(
    fileSystem = FileSystem.SYSTEM,
    serializer = PreferencesSerializer,
    producePath = {
        context.filesDir
            .resolve(DATA_STORE_FILE_NAME)
            .toOkioPath()
    },
)
