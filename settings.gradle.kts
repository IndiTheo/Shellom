rootProject.name = "shellom"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.library") version "9.2.1"
        id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
        id("com.vanniktech.maven.publish") version "0.37.0"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}
