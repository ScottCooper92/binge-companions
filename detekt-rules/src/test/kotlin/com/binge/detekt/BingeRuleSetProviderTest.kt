package com.binge.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSetProvider
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.compileAndLint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.ServiceLoader

private val RULE_IDS = setOf("BannerComment", "ConsecutiveInlineComments", "DeclarationCommentShouldBeKDoc", "KDocLength")

class BingeRuleSetProviderTest {
    @Test
    fun `the provider registers every comment rule under the binge id`() {
        val provider = BingeRuleSetProvider()

        assertEquals("binge", provider.ruleSetId)
        assertEquals(
            RULE_IDS,
            provider
                .instance(Config.empty)
                .rules
                .map { it.ruleId }
                .toSet(),
        )
    }

    @Test
    fun `detekt can discover the provider through its service file`() {
        val providers = ServiceLoader.load(RuleSetProvider::class.java).map { it::class }

        assertTrue(BingeRuleSetProvider::class in providers, "META-INF/services is missing or names another class: $providers")
    }

    @Test
    fun `detekt yml configures exactly the rules the provider registers, all active`() {
        val configured = bingeBlock(File(repositoryRoot(), "detekt.yml").readLines())

        assertEquals(RULE_IDS, configured.keys)
        RULE_IDS.forEach { id ->
            assertTrue(configured.getValue(id).any { it.trim() == "active: true" }, "$id is not active in detekt.yml")
        }
    }

    @Test
    fun `a configured threshold changes what a rule reports, so a mistyped key would show`() {
        val threeLines = "// one\n// two\n// three\nval x = 1\n"

        assertEquals(0, ConsecutiveInlineComments(TestConfig("threshold" to 3)).compileAndLint(threeLines).size)
        assertEquals(1, ConsecutiveInlineComments(TestConfig("threshold" to 2)).compileAndLint(threeLines).size)
    }

    @Test
    fun `the KDoc length keys are read from config`() {
        val twoLines = "/**\n * one\n * two\n */\nclass Foo\n"

        assertEquals(0, KDocLength().compileAndLint(twoLines).size)
        assertEquals(1, KDocLength(TestConfig("classThreshold" to 1)).compileAndLint(twoLines).size)
    }

    /** The rule ids under the top-level `binge:` key, each with its own indented lines. */
    private fun bingeBlock(lines: List<String>): Map<String, List<String>> {
        val start = lines.indexOfFirst { it.trimEnd() == "binge:" }
        assertTrue(start >= 0, "detekt.yml has no top-level `binge:` block")
        val block = lines.drop(start + 1).takeWhile { it.isBlank() || it.startsWith(" ") || it.startsWith("#") }
        val result = linkedMapOf<String, MutableList<String>>()
        var current: MutableList<String>? = null
        for (line in block) {
            val header = Regex("^ {2}(\\w+):\\s*$").matchEntire(line)
            if (header != null) {
                current = mutableListOf<String>().also { result[header.groupValues[1]] = it }
            } else {
                current?.add(line)
            }
        }
        return result
    }

    private fun repositoryRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "detekt.yml").exists()) dir = dir.parentFile
        return checkNotNull(dir) { "detekt.yml not found above ${System.getProperty("user.dir")}" }
    }
}
