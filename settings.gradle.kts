pluginManagement {
    val specmaticGradlePluginVersion = settings.extra["specmaticGradlePluginVersion"] as String
    plugins {
        id("io.specmatic.gradle") version (specmaticGradlePluginVersion)
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal {
            mavenContent {
                snapshotsOnly()
            }

            content {
                includeGroup("io.specmatic.gradle")
            }
        }

        maven {
            name = "specmaticPrivate"
            url = uri("https://maven.pkg.github.com/znsio/specmatic-private-maven-repo")
            credentials {
                username = listOf(
                    settings.extra.properties["github.actor"],
                    System.getenv("SPECMATIC_GITHUB_USER"),
                    System.getenv("ORG_GRADLE_PROJECT_specmaticPrivateUsername")
                ).firstNotNullOfOrNull { it }.toString()

                password = listOf(
                    settings.extra.properties["github.token"],
                    System.getenv("SPECMATIC_GITHUB_TOKEN"),
                    System.getenv("ORG_GRADLE_PROJECT_specmaticPrivatePassword")
                ).firstNotNullOfOrNull { it }.toString()
            }
        }

    }
}


rootProject.name = "specmatic"

include("specmatic-executable")
project(":specmatic-executable").projectDir = file("application")
include("core")
include("junit5-support")
