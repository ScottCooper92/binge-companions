plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
}

// Custom detekt rules, packaged as a plain JVM jar that :sdk's detekt run loads via detektPlugins
// (wired in sdk/build.gradle.kts). It must NOT apply the detekt plugin itself — detekt can't
// analyse the ruleset that defines it — and it carries no coverage gate: it has no production app
// logic of its own for a line-coverage floor to mean anything against. The PSI rules are covered
// by JUnit 5 tests (io.gitlab.arturbosch.detekt:detekt-test), the same harness Binge's copy uses.
//
// Ported from Binge's detekt-rules module per #39: this repository takes only the lexical comment
// rules (BannerComment, ConsecutiveInlineComments, DeclarationCommentShouldBeKDoc, KDocLength).
// FileLength and the Tv* focus rules stayed in Binge — one needs a repo-specific grandfather list,
// the rest don't apply outside Android TV code.
dependencies {
    compileOnly(libs.detekt.api)
    testImplementation(libs.detekt.test)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<Test> {
    useJUnitPlatform()
}
