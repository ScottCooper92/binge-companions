pluginManagement {
    repositories {
        // Google first, for its own groups: the Android Gradle plugin is published only there, and asking Central
        // first for it fails the build outright when Central answers 429.
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // Google's mirror of Maven Central, asked before Central. Shared build hosts (CI runners, cloud agent sessions)
        // are rate-limited by Central and get 429s mid-build, and Gradle treats a 429 as a failure rather than a miss,
        // so the build stops even when a later repository has the artifact. Anything the mirror lacks falls through.
        maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralMirror" }
        // The portal before Central: some plugins (ktlint's, for one) are published only there, and a 429 from
        // Central would fail the build before the portal was asked.
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // The same mirror, ahead of Central, for the same reason as above.
        maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralMirror" }
        mavenCentral()
    }
}

rootProject.name = "binge-companions"

include(":contracts")
include(":sdk")
include(":detekt-rules")
