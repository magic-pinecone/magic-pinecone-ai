package org.mpc.domain.repository

import org.mpc.domain.model.GraduationLanguage

interface GraduationLanguageRepository {
    /**
     * Reads the requirements from the configured Magic Pinecone backend. Returns null when no
     * backend is configured: this data has no static fallback.
     */
    suspend fun fetchRequirements(): GraduationLanguage?
}
