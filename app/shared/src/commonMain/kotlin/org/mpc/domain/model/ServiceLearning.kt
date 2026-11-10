package org.mpc.domain.model

import kotlinx.serialization.Serializable

/** `GET /api/v1/intl/service-learning`; see docs/api-contract.md. */
@Serializable
data class ServiceLearning(
    val contact: ServiceLearningContact,
    val requirements: List<ServiceLearningRequirement>,
    val openQuestions: List<String>,
)

@Serializable
data class ServiceLearningContact(
    val nameZh: String,
    val nameEn: String,
    val website: String,
    val sources: List<ServiceLearningSource>,
)

@Serializable
data class ServiceLearningRequirement(
    val id: String,
    val degrees: List<RuleDegree>,
    val titleZh: String,
    val titleEn: String,
    val summaryZh: String,
    val summaryEn: String,
    val sources: List<ServiceLearningSource>,
)

@Serializable
data class ServiceLearningSource(
    val url: String,
    val title: String,
    val fetched: String,
    val quote: String,
)

fun ServiceLearning.toSourcedRules() = SourcedRules(
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
            sources = it.sources.map { source -> source.toRuleSource() },
        )
    },
    openQuestions = openQuestions,
)

private fun ServiceLearningSource.toRuleSource() = RuleSource(url = url, title = title, fetched = fetched, quote = quote)
