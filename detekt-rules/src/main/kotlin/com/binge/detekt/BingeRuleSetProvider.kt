package com.binge.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

/**
 * Registers this repository's slice of Binge's custom detekt rules (#39). The ruleset id (`binge`)
 * is the namespace the rules are configured under in detekt.yml, matching Binge's own naming.
 * Loaded onto `:sdk`'s detekt run via `detektPlugins` in `sdk/build.gradle.kts`.
 *
 * Only the lexical comment rules are ported here — the ones that need no type resolution and no
 * repo-specific allowlist. `FileLength` and the `Tv*` focus rules stayed in Binge: see #39.
 */
class BingeRuleSetProvider : RuleSetProvider {
    override val ruleSetId: String = "binge"

    override fun instance(config: Config): RuleSet =
        RuleSet(
            ruleSetId,
            listOf(
                BannerComment(config),
                ConsecutiveInlineComments(config),
                DeclarationCommentShouldBeKDoc(config),
                KDocLength(config),
            ),
        )
}
