package com.binge.companion.minifycheck

import com.google.protobuf.GeneratedMessageLite
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Reads what R8 kept of [ProbeActivity] in the release build, and fails if a consumer's minified build would break
 * at runtime (#160). Both failures this guards against appear only on a device, and only in a minified build:
 *
 * - grpc-binder's name resolver is made with `Class.forName(...).getConstructor().newInstance()`. Without its
 *   constructor, every `BinderChannelBuilder.forAddress` throws `ServiceConfigurationError`.
 * - protobuf-javalite finds each field of a message by the name in its generated `newMessageInfo`. A field R8
 *   removed or renamed makes the first parse of that message throw `Field … not found`.
 *
 * R8 reports what it did in two files beside the release build's mapping. `mapping.txt` names every class it kept,
 * whatever it renamed it to. `seeds.txt` names every member a keep rule holds, under its original name. A member
 * held by a rule is neither removed nor renamed, so a seed is exactly what a lookup by name on a device would find.
 * The mapping alone cannot say that: it leaves out a member whose name and signature R8 did not change.
 */
class MinifiedKeepRulesTest {
    private val mapping = File(System.getProperty("minifyCheck.mapping"))
    private val kept: Set<String> = keptClasses(mapping)
    private val seeds: Map<String, Seeds> = readSeeds(mapping.resolveSibling("seeds.txt"))

    @Test
    fun `the probe keeps what a host keeps`() {
        val expected =
            listOf(
                "com.binge.companion.contracts.request.v1.HandshakeRequest",
                "com.binge.companion.contracts.library.v1.HandshakeRequest",
                "com.binge.companion.contracts.stream.v1.HandshakeRequest",
                "com.binge.companion.contracts.rpc.Status",
                "com.google.protobuf.Any",
                "com.google.protobuf.Timestamp",
                RESOLVER,
            )
        val missing = expected.filterNot { it in kept }
        assertTrue(missing.isEmpty(), "R8 removed classes a host keeps, so the checks below would prove nothing: $missing")
    }

    @Test
    fun `grpc-binder's name resolver keeps the constructor it is made with`() {
        assertTrue(
            "<init>()" in seeds[RESOLVER]?.methods.orEmpty(),
            "R8 removed $RESOLVER.<init>(), so every BinderChannelBuilder.forAddress would throw",
        )
    }

    @Test
    fun `every message keeps each field it is parsed by, under its own name`() {
        val messages = kept.mapNotNull(::generatedMessage)
        assertTrue(messages.size > MIN_MESSAGES, "only ${messages.size} messages were kept; the probe is not keeping the contracts")
        val broken =
            messages.flatMap { message ->
                val fields = seeds[message.name]?.fields.orEmpty()
                fieldNames(message).filterNot { it in fields }.map { "${message.name}.$it" }
            }
        assertTrue(broken.isEmpty(), "No keep rule holds these fields, which protobuf-javalite looks up by name: $broken")
    }

    /** What keep rules hold of one class, by original name. */
    private class Seeds {
        val fields = mutableSetOf<String>()

        /** As `name(parameters)`, with a constructor as `<init>(…)`. */
        val methods = mutableSetOf<String>()
    }

    private companion object {
        const val RESOLVER = "io.grpc.binder.internal.IntentNameResolverProvider"

        /** Comfortably under the contracts' message count, and far over what a probe keeping nothing would leave. */
        const val MIN_MESSAGES = 50

        val CLASS_LINE = Regex("""^(\S+) -> \S+:$""")

        // `com.Foo: type name` for a field, `com.Foo: type name(parameters)` for a method, `com.Foo: Foo(parameters)`
        // for a constructor, and `com.Foo` alone for the class.
        val SEED_LINE = Regex("""^([^:\s]+): (?:\S+ )?([^\s(]+)(\([^)]*\))?$""")

        /** Every class in the shrunk output, by its original name. */
        fun keptClasses(mapping: File): Set<String> {
            check(mapping.isFile) { "No R8 mapping at $mapping: the release build did not shrink" }
            return mapping.useLines { lines -> lines.mapNotNull { CLASS_LINE.find(it)?.groupValues?.get(1) }.toSet() }
        }

        fun readSeeds(file: File): Map<String, Seeds> {
            check(file.isFile) { "No R8 seeds at $file" }
            val seeds = mutableMapOf<String, Seeds>()
            file.forEachLine { line ->
                val (owner, name, parameters) = SEED_LINE.find(line)?.destructured ?: return@forEachLine
                val held = seeds.getOrPut(owner, ::Seeds)
                when {
                    parameters.isEmpty() -> held.fields += name
                    name == owner.substringAfterLast('.') -> held.methods += "<init>$parameters"
                    else -> held.methods += name + parameters
                }
            }
            return seeds
        }

        /** The class named [name], unshrunk, if it is a generated message. */
        fun generatedMessage(name: String): Class<*>? =
            runCatching { Class.forName(name, false, MinifiedKeepRulesTest::class.java.classLoader) }
                .getOrNull()
                ?.takeIf { GeneratedMessageLite::class.java.isAssignableFrom(it) && it != GeneratedMessageLite::class.java }

        /**
         * The field names [message] is parsed by: the strings in its generated `newMessageInfo` objects, which is
         * what protobuf-javalite reads to find each field by reflection. Asked of the message itself, through the
         * same `BUILD_MESSAGE_INFO` call the runtime makes.
         */
        fun fieldNames(message: Class<*>): List<String> {
            val instance = message.getMethod("getDefaultInstance").invoke(null)
            val dynamicMethod =
                message.getDeclaredMethod(
                    "dynamicMethod",
                    GeneratedMessageLite.MethodToInvoke::class.java,
                    Any::class.java,
                    Any::class.java,
                )
            dynamicMethod.isAccessible = true
            val info = dynamicMethod.invoke(instance, GeneratedMessageLite.MethodToInvoke.BUILD_MESSAGE_INFO, null, null)
            // A message with no fields has no objects at all.
            val objects = info.javaClass
                .getDeclaredField("objects")
                .apply { isAccessible = true }
                .get(info) as Array<*>?
            return objects.orEmpty().filterIsInstance<String>()
        }
    }
}
