package com.binge.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.com.intellij.psi.PsiComment

/**
 * Flags banner / ruler comments — a comment carrying a run of repeated ruler characters
 * (`──────`, `======`, `------`), with or without a section title. Group related code into a
 * well-named type or file instead (CLAUDE.md "Comments": no banner/ruler comments).
 */
class BannerComment(
    config: Config = Config.empty,
) : Rule(config) {
    override val issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Style,
        description = "Banner/ruler comments are disallowed; name a type or file for the section instead.",
        debt = Debt.FIVE_MINS,
    )

    override fun visitComment(comment: PsiComment) {
        super.visitComment(comment)
        if (hasRulerRun(comment.text)) {
            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(comment),
                    message = "This looks like a banner/ruler comment. Remove it and group the code " +
                        "into a well-named type or file.",
                ),
            )
        }
    }

    private fun hasRulerRun(text: String): Boolean {
        var last = ' '
        var run = 0
        for (ch in text) {
            if (ch in RULER_CHARS) {
                run = if (ch == last) run + 1 else 1
                if (run >= MIN_RULER_RUN) return true
            } else {
                run = 0
            }
            last = ch
        }
        return false
    }

    companion object {
        private const val MIN_RULER_RUN = 3

        // Em-dash (—) is deliberately excluded: it is heavily used as prose punctuation here.
        private val RULER_CHARS = "-=_~─═━".toSet()
    }
}
