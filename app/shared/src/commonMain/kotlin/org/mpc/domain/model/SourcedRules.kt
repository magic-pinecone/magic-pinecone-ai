package org.mpc.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Rules the university sets, each with the official text it comes from. Service learning and
 * credits share this shape; each endpoint maps its own response onto it.
 */
data class SourcedRules(
    val contact: RuleContact,
    val rules: List<SourcedRule>,
    /** What the official documents leave unconfirmed. Shown apart from the rules so "not written" is not read as "not required". */
    val openQuestions: List<String>,
)

data class SourcedRule(
    val id: String,
    /** The degrees the rule applies to; empty means all of them. */
    val degrees: List<RuleDegree>,
    val title: LocalizedText,
    val summary: LocalizedText,
    /** Credits the rule asks for, when it is a number of credits. */
    val credits: Int? = null,
    val sources: List<RuleSource>,
)

data class RuleContact(
    val name: LocalizedText,
    val website: String,
    val sources: List<RuleSource>,
)

/** An official document and the passage of it that backs a rule. */
data class RuleSource(
    val url: String,
    val title: String,
    /** The day the document was read. */
    val fetched: String,
    val quote: String,
)

@Serializable
enum class RuleDegree {
    @SerialName("bachelor")
    BACHELOR,

    @SerialName("graduate")
    GRADUATE,
}

/** The rules that apply to [degree]; null keeps them all. */
fun SourcedRules.forDegree(degree: RuleDegree?): List<SourcedRule> = if (degree == null) rules else rules.filter { it.degrees.isEmpty() || degree in it.degrees }

/** The degrees any rule names, in declaration order. */
val SourcedRules.degrees: List<RuleDegree> get() = RuleDegree.entries.filter { degree -> rules.any { degree in it.degrees } }
