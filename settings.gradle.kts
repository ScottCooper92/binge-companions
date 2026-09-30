pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "binge-integrations"

include(":contracts")
include(":sdk")
include(":detekt-rules")
