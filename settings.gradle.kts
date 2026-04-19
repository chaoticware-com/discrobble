rootProject.name = "discrobble"

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
    }
}

include(":androidApp")
include(":shared:domain")
include(":shared:auth")
include(":shared:catalog")
include(":shared:session")
include(":shared:scrobble")
include(":shared:persistence")
include(":shared:network")
include(":shared:di")
