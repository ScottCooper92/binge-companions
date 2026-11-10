plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ktlint)
    id("binge.gates.detekt")
    alias(libs.plugins.kover)
}

group = "io.github.scottcooper92"
version = "0.1.0-SNAPSHOT"

android {
    namespace = "com.binge.companion.kit"
    compileSdk = 37

    defaultConfig {
        // The same floor as the SDK, so a companion that uses both targets one range of devices.
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
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    // The kit does not depend on :binge-companion-sdk, and the SDK and the contracts do not depend on the kit.
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// The kit is outside the contract's versioning promise (see its README): detekt and ktlint run as they do on the SDK.
detektGate {
    customRulesProject = ":detekt-rules"
}

// Coverage gates the kit, with nothing excluded. The floor is set from a measurement, not inherited: 97.4% with
// AddressRange alone, so 95. It ratchets as pieces land; see CLAUDE.md > Gates.
kover {
    reports {
        verify {
            rule {
                minBound(95)
            }
        }
    }
}
