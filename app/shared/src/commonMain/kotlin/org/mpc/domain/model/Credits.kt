package org.mpc.domain.model

import kotlinx.serialization.Serializable

/** `GET /api/v1/intl/credits`; see docs/api-contract.md. Same shape as service learning, plus the credits a rule is about. */
@Serializable
data class Credits(
    val contact: ServiceLearningContact,
    val requirements: List<CreditsRequirement>,
    val openQuestions: List<String>,
)

@Serializable
data class CreditsRequirement(
    val id: String,
    val degrees: List<RuleDegree>,
    val titleZh: String,
    val titleEn: String,
    val summaryZh: String,
    val summaryEn: String,
    /** Null when the rule names no number; 0 when the course is required but carries no credits. */
    val credits: Double? = null,
    val sources: List<ServiceLearningSource>,
)

fun Credits.toSourcedRules() = SourcedRules(
    contact = RuleContact(
        name = LocalizedText(zh = contact.nameZh, en = contact.nameEn),
        website = contact.website,
        sources = contact.sources.map { it.toRuleSource() },
    ),
    rules = requirements.map {
        SourcedRule(
            id = it.id,
            degrees = it.degrees,
            title = LocalizedText(zh = it.titleZh, en = it.titleEn),
            summary = LocalizedText(zh = it.summaryZh, en = it.summaryEn),
            credits = it.credits,
            sources = it.sources.map { source -> source.toRuleSource() },
        )
    },
    openQuestions = openQuestions,
)
