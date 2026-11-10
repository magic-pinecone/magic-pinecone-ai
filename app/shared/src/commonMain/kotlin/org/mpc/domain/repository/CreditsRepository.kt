package org.mpc.domain.repository

/** Distinct from [SourcedRulesRepository] so dependency injection can tell the rule sets apart. */
interface CreditsRepository : SourcedRulesRepository
