package com.binge.detekt

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

/**
 * Registers this repository's slice of Binge's custom detekt rules (#39). The ruleset id (`binge`)
 * is the namespace the rules are configured under in detekt.yml, matching Binge's own naming.
 * Loaded onto `:sdk`'s detekt run through binge-ci's shared gate, as its `customRulesProject`.
 *
 * Only the lexical comment rules are ported here — the ones that need no type resolution and no
 * repo-specific allowlist. `FileLength` and the `Tv*` focus rules stayed in Binge: see #39.
 */
class BingeRuleSetProvider : RuleSetProvider {
    override val ruleSetId: RuleSetId = RuleSetId("binge")

    override fun instance(): RuleSet =
        RuleSet(
            ruleSetId,
            listOf(
                ::BannerComment,
                ::ConsecutiveInlineComments,
                ::DeclarationCommentShouldBeKDoc,
                ::KDocLength,
            ),
        )
}
