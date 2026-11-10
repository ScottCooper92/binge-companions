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
        // Robolectric reads the merged test config this switches on, to find the SDK level and the manifest. It runs
        // the real KeyGenParameterSpec and Log that the Keystore cipher uses.
        unitTests.isIncludeAndroidResources = true
        // The plain JVM tests reach android.util.Log, which logs nothing there.
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.useJUnitPlatform()
            // Robolectric fetches its android-all jars itself, outside Gradle's repositories; point it at the mirror
            // the SDK uses, for the same reason (Central answers bursts from shared runners with a 429).
            it.systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2/")
            it.systemProperty("robolectric.dependency.repo.id", "MavenCentralMirror")
        }
    }
}

dependencies {
    // The kit does not depend on :binge-companion-sdk, and the SDK and the contracts do not depend on the kit.
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    // The Keystore half of the cipher runs under Robolectric, a JUnit 4 runner, on the vintage engine, as in the SDK.
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// The kit is outside the contract's versioning promise (see its README): detekt and ktlint run as they do on the SDK.
detektGate {
    customRulesProject = ":detekt-rules"
}

// Coverage gates the kit, with nothing excluded. The floor is set from a measurement, not inherited: 98.6% with
// AddressRange and SecretCipher, so 95. It ratchets as pieces land; see CLAUDE.md > Gates.
kover {
    reports {
        verify {
            rule {
                minBound(95)
            }
        }
    }
}
