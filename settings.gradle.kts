pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Carda"
include(":app", ":core:model", ":core:ppg", ":core:camera", ":core:measurement", ":core:auth", ":core:data", ":core:export", ":feature:measurement", ":feature:profile", ":feature:history", ":feature:dashboard", ":feature:result")
include(":feature:onboarding")
include(":core:ml")
