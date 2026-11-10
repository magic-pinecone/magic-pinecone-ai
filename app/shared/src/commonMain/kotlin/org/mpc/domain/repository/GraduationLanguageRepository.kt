package org.mpc.domain.repository

import kotlinx.coroutines.flow.Flow
import org.mpc.domain.model.GraduationLanguage

interface GraduationLanguageRepository {
    /** The backend the requirements are read from, or null when none is configured. Emits again when it changes. */
    val backend: Flow<String?>

    /**
     * Reads the requirements from the configured Magic Pinecone backend. Returns null when no
     * backend is configured: this data has no static fallback.
     */
    suspend fun fetchRequirements(): GraduationLanguage?
}
