package org.mpc

import android.app.Application
import org.mpc.core.createDataStore
import org.mpc.core.createDatabase
import org.mpc.core.createDatabaseBuilder
import org.mpc.data.html.JsoupHtmlParser
import org.mpc.di.AppGraph
import org.mpc.di.createAppGraph

class MpcApplication : Application() {
    lateinit var appGraph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()

        val storage = createDataStore(this)
        val dataStore = createDataStore(storage)
        val database =
            createDatabase(
                createDatabaseBuilder(this),
            )

        appGraph = createAppGraph(dataStore, database, JsoupHtmlParser())
    }
}
