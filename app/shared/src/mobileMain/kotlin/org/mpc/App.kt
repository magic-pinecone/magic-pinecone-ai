package org.mpc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import org.mpc.di.AppGraph

@Composable
fun ProvideAppDependencies(
    appGraph: AppGraph,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalMetroViewModelFactory provides appGraph.metroViewModelFactory,
        content = content,
    )
}
