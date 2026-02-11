pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "PlexHubTV"
include(":app")
include(":core:model")
include(":core:common")
include(":domain")
include(":core:network")
include(":core:navigation")
include(":core:database")
include(":core:datastore")
include(":core:designsystem")
include(":core:ui")
include(":data")
 