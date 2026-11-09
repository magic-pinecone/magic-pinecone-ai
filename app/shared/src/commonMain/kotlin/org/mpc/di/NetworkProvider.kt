package org.mpc.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import io.ktor.client.HttpClient
import org.mpc.core.createHttpClient

@ContributesTo(AppScope::class)
@BindingContainer
class NetworkProvider {
    @Provides
    fun provideHttpClient(): HttpClient = createHttpClient()
}
