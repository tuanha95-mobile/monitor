pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "measure-android"
include(":measure")
// Temporarily excluded while migrating the toolchain (Gradle 9 / AGP 9 / Kotlin 2.2).
// Re-enable once the core :measure module builds cleanly.
// include(":benchmarks")
// include(":baseline-target")
