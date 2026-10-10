plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ktlint)
}

// No kover, and no detekt: this module ships nothing. Its probe exists to be shrunk, its test reads R8's output,
// and its round-trip Service exists to be bound by the instrumented test, so none of it is code to cover (#160).

android {
    namespace = "com.binge.companion.minifycheck"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.binge.companion.minifycheck"
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The instrumented round trip runs on `minified`: release's shrinking, signed with the debug key so it installs,
    // and with its own source set holding the Service the test binds (#160). Release stays the probe alone, so the
    // members MinifiedKeepRulesTest reads are kept by the consumer rules and nothing else.
    testBuildType = "minified"

    // Shrunk the way a consumer's release build is: AGP's default optimized rules, plus the consumer rules
    // every dependency ships. Nothing of this module's own is kept, because that is exactly what could mask a
    // missing consumer rule.
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
        create("minified") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
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

        managedDevices {
            localDevices {
                // The round trip's emulator, driven by device-round-trip.yml and never by ci.yml. aosp-atd is headless
                // and carries no Google APIs, which a Binder round trip does not need.
                create("roundTripAtdApi34") {
                    device = "Pixel 6"
                    apiLevel = 34
                    systemImageSource = "aosp-atd"
                }
            }
        }
    }
}

dependencies {
    implementation(project(":sdk"))

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)

    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
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
    // Neither shrunk build type runs the JVM tests: they load each message as generated, which a shrunk build is not.
    listOf("release", "minified").forEach { buildType ->
        beforeVariants(selector().withBuildType(buildType)) { variant ->
            variant.hostTests.values.forEach { it.enable = false }
        }
    }
}
