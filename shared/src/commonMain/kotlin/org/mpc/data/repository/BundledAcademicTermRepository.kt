package org.mpc.data.repository

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.datetime.LocalDate
import org.mpc.domain.model.AcademicTerm
import org.mpc.domain.repository.AcademicTermRepository

// From the NCU academic calendar (https://pdc.adm.ncu.edu.tw/static/file/19/1019/img/238/calendar115.pdf).
private val bundledTerms = listOf(
    AcademicTerm("115-1", LocalDate(2026, 9, 7), LocalDate(2026, 12, 24)),
).associateBy { it.semester }

@ContributesBinding(AppScope::class)
@Inject
internal class BundledAcademicTermRepository : AcademicTermRepository {
    override suspend fun term(semester: String): AcademicTerm? = bundledTerms[semester]
}
