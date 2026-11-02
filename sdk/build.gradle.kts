plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ktlint)
    id("binge.gates.detekt")
    alias(libs.plugins.kover)
}

group = "io.github.scottcooper92"
version = "0.1.0-SNAPSHOT"

android {
    namespace = "com.binge.companion.sdk"
    compileSdk = 37

    defaultConfig {
        // 26 matches Binge's own floor so a companion can target the same devices. Binge only
        // discovers companions on API 28 and above (the signing-lineage check needs it), which the
        // caller policy here mirrors by refusing below 28 rather than checking weakly.
        minSdk = 26

        // The one rule a minifying host needs and grpc-binder does not ship; see the file.
        consumerProguardFiles("consumer-rules.pro")
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
        // Robolectric reads the merged test config this switches on, to find the SDK level and the manifest.
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.useJUnitPlatform()
            // Robolectric fetches its android-all jars itself, outside Gradle's repositories. Central answers
            // bursts from shared runners with a 429, so point it at the mirror binge-seerr uses for the same reason.
            it.systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2/")
            it.systemProperty("robolectric.dependency.repo.id", "MavenCentralMirror")
        }
    }

    // Test helpers a companion's own tests can use, published apart from the runtime: a consumer
    // asks for them with `testImplementation(testFixtures(...))`, and its APK never carries them.
    testFixtures {
        enable = true
    }
}

dependencies {
    // api: a companion implements the generated service base and speaks grpc-binder's types
    // (SecurityPolicy, Status), so both are part of this library's surface, not details of it.
    api(project(":binge-companion-contracts"))
    api(libs.grpc.binder)
    // `OffsetPagingSource` is a `PagingSource` and `OpenPagingSources` takes and returns them, so the type is
    // part of their surface. paging-common is the JVM artifact: no Android, no Room, no Compose.
    api(libs.paging.common)

    // The fixtures' awaitEvent is written against kotlinx.coroutines; :binge-companion-contracts already exposes it
    // to the runtime, so this only names it for the fixtures' own compile.
    testFixturesApi(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.grpc.inprocess)
    // The Intent and PackageManager halves of the hand-off readers (#280). Robolectric is a JUnit 4 runner, so the
    // vintage engine runs it on the same JUnit Platform task as the Jupiter tests, which stay as they are.
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// detekt analyses `:binge-companion-sdk` and not `:binge-companion-contracts`: the only Kotlin under contracts/src/main is protoc's
// output, which is not checked in, and its one hand-written file is a test. binge-ci's shared gate
// pins it to src/main, for the same reason: a test's shape is not the SDK's contract. It also wires
// the debug compilation's classpath on, without which UnsafeCallOnNullableType, the `!!` ban, loads
// and silently never fires. Binge's custom `binge:` comment rules (#39) load onto the same run.
detektGate {
    customRulesProject = ":detekt-rules"
}

// Coverage gates :binge-companion-sdk and not :binge-companion-contracts: generated protobuf and grpc-kotlin stubs would swamp the
// figure with code no test should be written for. Nothing in the SDK is excluded: Robolectric runs in `build` (#281), so the
// classes that hold an Android type are tested through ShadowPackageManager and Robolectric's service controller (#282) -
// HostPolicy and the PackageManager extension, HandOffPolicy's factories and isStartable, and IntegrationService. Their
// decisions are still split out (HostSecurityPolicy, HandOffCallerPolicy, HandOffTargetPolicy, signerDigests) and tested on the
// plain JVM. The floor was set from a measurement, 99.2% with nothing excluded, so it ratchets: raise it as the SDK grows.
kover {
    reports {
        verify {
            rule {
                minBound(95)
            }
        }
    }
}
