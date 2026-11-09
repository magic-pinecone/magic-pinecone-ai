package org.mpc.presentation.viewModel

import kotlinx.coroutines.CancellationException

internal fun Throwable.rethrowIfCancellationOrFatal() {
    if (this is CancellationException || this !is Exception) {
        throw this
    }
}
