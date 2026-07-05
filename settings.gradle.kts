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
        // JitPack — consumers add this too (see README). Harmless for the build itself.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "whisper"
include(":library")
include(":sample")
