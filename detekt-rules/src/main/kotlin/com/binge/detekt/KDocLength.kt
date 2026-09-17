package com.binge.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty

/**
 * Caps the prose of a KDoc on a class, function or property (CLAUDE.md "Comments"). Counted lines
 * are the non-blank content lines between `/**` and `*/`, minus block tags (`@param`, `@return`, …)
 * and their indented continuations — parameter docs scale with the signature, not with how much
 * the author had to say. Past the cap the explanation belongs in `docs/`, or the code wants to be
 * clearer.
 */
class KDocLength(
    config: Config = Config.empty,
) : Rule(config) {
    override val issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Style,
        description = "A KDoc longer than the cap; move the essay into docs/ or make the code say it.",
        debt = Debt.TEN_MINS,
    )

    private val classThreshold: Int = valueOrDefault(CLASS_KEY, DEFAULT_THRESHOLD)
    private val functionThreshold: Int = valueOrDefault(FUNCTION_KEY, DEFAULT_THRESHOLD)
    private val propertyThreshold: Int = valueOrDefault(PROPERTY_KEY, DEFAULT_THRESHOLD)

    override fun visitClassOrObject(classOrObject: KtClassOrObject) {
        super.visitClassOrObject(classOrObject)
        check(classOrObject, classThreshold, "class")
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        check(function, functionThreshold, "function")
    }

    override fun visitProperty(property: KtProperty) {
        super.visitProperty(property)
        check(property, propertyThreshold, "property")
    }

    private fun check(
        declaration: KtDeclaration,
        threshold: Int,
        kind: String,
    ) {
        val doc = declaration.docComment ?: return
        val lines = proseLineCount(doc.text)
        if (lines > threshold) {
            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(declaration),
                    message = "KDoc on $kind '${declaration.name}' is $lines lines of prose, exceeding the " +
                        "$threshold-line cap. Move the essay into docs/, or make the code say it.",
                ),
            )
        }
    }

    companion object {
        private const val CLASS_KEY = "classThreshold"
        private const val FUNCTION_KEY = "functionThreshold"
        private const val PROPERTY_KEY = "propertyThreshold"
        private const val DEFAULT_THRESHOLD = 6
        private val LINE_PREFIX = Regex("""^\s*(/\*\*|\*/|\*)?\s?""")

        /** Non-blank KDoc content lines, excluding block-tag lines and the indented lines continuing them. */
        fun proseLineCount(kdocText: String): Int {
            var count = 0
            var inTag = false
            for (raw in kdocText.lines()) {
                val line = raw.replace(LINE_PREFIX, "").trimEnd()
                if (line.isBlank()) continue
                if (line.startsWith("@")) {
                    inTag = true
                    continue
                }
                if (inTag && line.first().isWhitespace()) continue
                inTag = false
                count++
            }
            return count
        }
    }
}
