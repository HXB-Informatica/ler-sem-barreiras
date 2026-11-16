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

rootProject.name = "ler-sem-barreiras"

include(
    ":app",
    ":core",
    ":alignment",
    ":audio",
    ":speech",
    ":data",
    ":feature-teacher",
    ":feature-reader",
)
