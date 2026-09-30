package com.binge.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFile

/**
 * Flags a block of more than [threshold] consecutive standalone `//` comment lines. The bar caps
 * inline comments at two lines — a genuinely subtle invariant may earn a third — so past the
 * threshold the rationale belongs in a KDoc on the enclosing declaration, or the code wants
 * restructuring. Trailing comments (sharing a line with code) and block/KDoc comments are ignored.
 */
class ConsecutiveInlineComments(
    config: Config = Config.empty,
) : Rule(config) {
    override val issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Style,
        description = "A run of inline // comments longer than the cap; move the rationale into a " +
            "KDoc on the enclosing declaration, or restructure the code.",
        debt = Debt.TEN_MINS,
    )

    private val threshold: Int = valueOrDefault(THRESHOLD_KEY, DEFAULT_THRESHOLD)

    override fun visitKtFile(file: KtFile) {
        super.visitKtFile(file)
        val text = file.text
        val standalone = PsiTreeUtil
            .collectElementsOfType(file, PsiComment::class.java)
            .asSequence()
            .filter { it.tokenType == KtTokens.EOL_COMMENT && isStandalone(it, text) }
            .map { it to lineOf(text, it.textRange.startOffset) }
            .sortedBy { it.second }
            .toList()

        var anchor: PsiComment? = null
        var runLen = 0
        var prevLine = Int.MIN_VALUE
        for ((comment, line) in standalone) {
            if (line == prevLine + 1) {
                runLen++
            } else {
                flush(anchor, runLen)
                anchor = comment
                runLen = 1
            }
            prevLine = line
        }
        flush(anchor, runLen)
    }

    private fun flush(anchor: PsiComment?, runLen: Int) {
        if (anchor != null && runLen > threshold) {
            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(anchor),
                    message = "$runLen consecutive // comment lines exceed the $threshold-line cap. " +
                        "Move the rationale into a KDoc on the enclosing declaration, or restructure.",
                ),
            )
        }
    }

    private fun isStandalone(comment: PsiComment, text: String): Boolean {
        val start = comment.textRange.startOffset
        val lineStart = text.lastIndexOf('\n', start - 1) + 1
        return text.substring(lineStart, start).isBlank()
    }

    private fun lineOf(text: String, offset: Int): Int {
        var line = 0
        val end = minOf(offset, text.length)
        for (i in 0 until end) {
            if (text[i] == '\n') line++
        }
        return line
    }

    companion object {
        private const val THRESHOLD_KEY = "threshold"
        private const val DEFAULT_THRESHOLD = 3
    }
}
