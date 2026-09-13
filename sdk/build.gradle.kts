plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ktlint)
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
