plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

group = "io.github.scottcooper92"
version = "0.1.0-SNAPSHOT"

android {
    namespace = "com.binge.integration.sdk"
    compileSdk = 37

    defaultConfig {
        // 26 matches Binge's own floor so a companion can target the same devices. Binge only
        // discovers companions on API 28 and above (the signing-lineage check needs it), which the
        // caller policy here mirrors by refusing below 28 rather than checking weakly.
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}

dependencies {
    // api: a companion implements the generated service base and speaks grpc-binder's types
    // (SecurityPolicy, Status), so both are part of this library's surface, not details of it.
    api(project(":contracts"))
    api(libs.grpc.binder)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.grpc.inprocess)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// detekt analyses `:sdk` and not `:contracts`: the only Kotlin under contracts/src/main is protoc's
// output, which is not checked in, and its one hand-written file is a test. Pinned to src/main for
// the same reason - a test's shape is not the SDK's contract.
detekt {
    config.setFrom(layout.settingsDirectory.file("detekt.yml"))
    buildUponDefaultConfig = true
    parallel = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    setSource(fileTree("src/main") { include("**/*.kt") })
    jvmTarget = "17"
    reports {
        xml.required.set(false)
        txt.required.set(false)
        sarif.required.set(false)
        md.required.set(false)
    }
}

// UnsafeCallOnNullableType - the `!!` ban - needs type resolution, and a detekt task has no
// classpath by default, so without this the rule loads and silently never fires. `libraries` is
// the compile task's already-variant-resolved classpath; resolving compileDependencyFiles directly
// trips AGP 9 variant ambiguity. configureEach rather than a lookup, because the Android variant
// compilations do not exist yet when the Kotlin plugin applies.
kotlin.target.compilations.configureEach {
    if (name != "debug") return@configureEach
    val classpathFiles =
        compileTaskProvider.map {
            (it as org.jetbrains.kotlin.gradle.tasks.KotlinCompileTool).libraries
        }
    val outputClasses = output.classesDirs
    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        classpath.from(classpathFiles, outputClasses)
    }
}
