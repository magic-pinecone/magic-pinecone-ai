package org.mpc.domain.repository

import kotlinx.coroutines.flow.Flow
import org.mpc.domain.model.AssistantAnswer

interface AssistantRepository {
    /** The backend that is asked, or null when none is configured. Emits again when it changes. */
    val backend: Flow<String?>

    /** Asks the configured backend. Null when no backend is configured. */
    suspend fun ask(question: String): AssistantAnswer?
}
