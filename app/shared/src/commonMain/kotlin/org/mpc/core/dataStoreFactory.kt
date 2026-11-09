package org.mpc.core

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Storage
import androidx.datastore.preferences.core.Preferences

fun createDataStore(storage: Storage<Preferences>): DataStore<Preferences> = DataStoreFactory.create(storage = storage)

internal const val DATA_STORE_FILE_NAME = "mpc_config.preferences_pb"
