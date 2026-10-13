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
        unitTests.all { it.useJUnitPlatform() }
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
// figure with code no test should be written for. The exclusions below are the same idea one level
// down - each is a class that exists only to hold an Android type, and each already has its
// JVM-testable half split out and covered, which the SDK's own KDoc says at every one of them:
// HandOffPolicy hands to HandOffCallerPolicy, HostPolicy to HostSecurityPolicy and signerDigests,
// IntegrationService to inboundParcelablePolicy (its per-service registration needs grpc-binder's
// Android statics, so it has no JVM test). AdvancedRequestKt
// is deliberately NOT excluded - it holds both halves, so excluding it would drop a tested function
// from the measurement.
kover {
    reports {
        filters {
            excludes {
                classes(
                    // Context + PackageManager + Log factories, and the PackageManager extension.
                    "com.binge.companion.sdk.HostPolicy",
                    "com.binge.companion.sdk.HostPolicy\$*",
                    "com.binge.companion.sdk.HostPolicyKt",
                    "com.binge.companion.sdk.HandOffPolicy",
                    // An android.app.Service standing up a Binder-transport gRPC server. There is
                    // no JVM unit test of this short of an instrumented one.
                    "com.binge.companion.sdk.IntegrationService",
                    // Context + PackageManager + Intent construction for PlayTarget.toIntent. Its
                    // decision (PlayTargetKt's resolveIntent) is split into its own file precisely so
                    // it is NOT in this facade class and stays covered; PlayTargetIntentKt is the
                    // thin Android wrapper around it, same split as HostPolicy/HandOffPolicy above.
                    "com.binge.companion.sdk.PlayTargetIntentKt",
                )
            }
        }
        verify {
            rule {
                minBound(85)
            }
        }
    }
}
