plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ktlint)
}

// No kover, and no detekt: this module ships nothing. Its one class is a probe that exists to be shrunk,
// and its test reads R8's output rather than covering code of its own (#160).

android {
    namespace = "com.binge.companion.minifycheck"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.binge.companion.minifycheck"
        minSdk = 26
        targetSdk = 36
    }

    // Shrunk the way a consumer's release build is: AGP's default optimized rules, plus the consumer rules
    // every dependency ships. Nothing of this module's own is kept, because that is exactly what could mask a
    // missing consumer rule.
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
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
    implementation(project(":sdk"))

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// The test reads the release build's R8 mapping, so it runs once, after that build has shrunk. The unit tests
// themselves run unshrunk, which is what lets them load each message as it was generated and ask it for its
// field names.
val mapping = layout.buildDirectory.file("outputs/mapping/release/mapping.txt")

tasks.withType<Test>().configureEach {
    dependsOn("minifyReleaseWithR8")
    inputs.file(mapping).withPropertyName("r8Mapping")
    systemProperty("minifyCheck.mapping", mapping.get().asFile.absolutePath)
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant ->
        variant.hostTests.values.forEach { it.enable = false }
    }
}
