package org.mpc.domain.repository

import org.mpc.domain.model.AcademicTerm

interface AcademicTermRepository {
    /** The term's class days, or null when they are not known for [semester]. */
    suspend fun term(semester: String): AcademicTerm?
}
