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

rootProject.name = "investment-tracker"

include(":app")
include(":core:model")
include(":core:parsing")
include(":core:data")
include(":core:sync")
include(":feature:portfolio")
include(":feature:import")
