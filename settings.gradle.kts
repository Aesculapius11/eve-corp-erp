pluginManagement {
    repositories {
        google()
        maven("https://maven.mozilla.org/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        google()
        maven("https://maven.mozilla.org/maven2/")
        mavenCentral()
    }
}

rootProject.name = "EveCorpERP"
include(":app")
