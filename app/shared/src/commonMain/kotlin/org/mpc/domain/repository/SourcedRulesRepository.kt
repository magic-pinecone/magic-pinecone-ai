package org.mpc.domain.repository

import kotlinx.coroutines.flow.Flow
import org.mpc.domain.model.SourcedRules

interface SourcedRulesRepository {
    /** The backend the rules are read from, or null when none is configured. Emits again when it changes. */
    val backend: Flow<String?>

    /** Reads the rules from the configured backend. Returns null when none is configured: there is no static fallback. */
    suspend fun fetchRules(): SourcedRules?
}
