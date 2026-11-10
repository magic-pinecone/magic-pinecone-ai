package org.mpc

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mpc.core.StorageIOException
import org.mpc.domain.model.AppLanguage

class MainActivity : AppCompatActivity() {
    private var appLocaleSyncJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val appGraph = (application as MpcApplication).appGraph

        setContent {
            AndroidAppShell(appGraph)
        }
    }

    override fun onResume() {
        super.onResume()
        appLocaleSyncJob?.cancel()
        appLocaleSyncJob = lifecycleScope.launch {
            val platformLanguage = androidAppLanguage() ?: return@launch
            val appSettingsRepository = (application as MpcApplication).appGraph.appSettingsRepository
            val persistedLanguage = appSettingsRepository.settings.first().language
            if (persistedLanguage != platformLanguage) {
                try {
                    appSettingsRepository.setLanguage(platformLanguage)
                } catch (_: StorageIOException) {
                    // Keep the platform locale active; the next resume will retry the sync.
                }
            }
        }
    }
}
