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
    fun `detekt yml sets exactly the sub-keys each rule reads, so a mistyped key fails here`() {
        val configured = bingeBlock(File(repositoryRoot(), "detekt.yml").readLines())

        assertEquals(setOf("threshold"), subKeys(configured.getValue("ConsecutiveInlineComments")).keys)
        assertEquals(
            setOf("classThreshold", "functionThreshold", "propertyThreshold"),
            subKeys(configured.getValue("KDocLength")).keys,
        )
    }

    @Test
    fun `ConsecutiveInlineComments configured from detekt yml allows three lines and flags four`() {
        val rule = ConsecutiveInlineComments(fileConfig("ConsecutiveInlineComments"))

        assertEquals(0, rule.compileAndLint(commentRun(3) + "val x = 1\n").size)
        assertEquals(
            1,
            ConsecutiveInlineComments(fileConfig("ConsecutiveInlineComments")).compileAndLint(commentRun(4) + "val x = 1\n").size,
        )
    }

    @Test
    fun `KDocLength configured from detekt yml allows six prose lines and flags seven, for class, function and property`() {
        listOf("class Foo", "fun foo() = Unit", "val foo = 1").forEach { declaration ->
            assertEquals(
                0,
                KDocLength(fileConfig("KDocLength")).compileAndLint(kdoc(6) + declaration + "\n").size,
                "6 lines above `$declaration`",
            )
            assertEquals(
                1,
                KDocLength(fileConfig("KDocLength")).compileAndLint(kdoc(7) + declaration + "\n").size,
                "7 lines above `$declaration`",
            )
        }
    }

    /** The real `detekt.yml` values for [ruleId], as the config detekt itself would hand the rule. */
    private fun fileConfig(ruleId: String): TestConfig {
        val configured = bingeBlock(File(repositoryRoot(), "detekt.yml").readLines())
        return TestConfig(*subKeys(configured.getValue(ruleId)).map { (key, value) -> key to value }.toTypedArray())
    }

    private fun subKeys(lines: List<String>): Map<String, Int> =
        lines
            .mapNotNull { Regex("^ {4}(\\w+):\\s*(\\d+)\\s*(#.*)?$").matchEntire(it) }
            .associate { it.groupValues[1] to it.groupValues[2].toInt() }

    private fun commentRun(lines: Int): String = (1..lines).joinToString("") { "// line $it\n" }

    private fun kdoc(lines: Int): String = "/**\n" + (1..lines).joinToString("") { " * prose line $it\n" } + " */\n"

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
