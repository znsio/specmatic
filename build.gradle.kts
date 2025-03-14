import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    `kotlin-dsl`
    id("io.specmatic.gradle") version ("0.0.31-SNAPSHOT")
}

allprojects {
    repositories {
        mavenCentral()
        mavenLocal()
    }
}


specmatic {
    kotlinVersion = "1.9.25"
    kotlinApiVersion = KotlinVersion.KOTLIN_1_9
    publishTo("specmaticPrivate", "https://maven.pkg.github.com/znsio/specmatic-private-maven-repo")
    publishToMavenCentral()

    withOSSLibrary(project(":core")) {
        publish {
            artifactId = "specmatic-core"
            pom {
                name = "Specmatic"
                description =
                    "Turn your contracts into executable specifications. Contract Driven Development - Collaboratively Design & Independently Deploy MicroServices & MicroFrontends."
                commonPomContents()
            }
        }
    }

    withOSSLibrary(project(":junit5-support")) {
        publish {
            artifactId = "junit5-support"
            pom {
                name = "SpecmaticJUnit5Support"
                description = "Specmatic JUnit 5 Support"
                commonPomContents()
            }
        }
    }

    withOSSApplication(project(":application")) {
        mainClass = "application.SpecmaticApplication"
        dockerBuild()
        shadow()
        publish {
            artifactId = "specmatic-executable"
            pom {
                name = "Specmatic Executable"
                description = "Command-line standalone executable jar for Specmatic"
                commonPomContents()
            }
        }
    }
}

private fun MavenPom.commonPomContents() {
    url = "https://specmatic.io"

    licenses {
        license {
            name = "MIT"
            url = "https://github.com/znsio/specmatic/blob/main/License.md"
        }
    }
    developers {
        developer {
            id = "specmaticBuilders"
            name = "Specmatic Builders"
            email = "info@specmatic.io"
        }
    }
    scm {
        connection = "https://github.com/znsio/specmatic"
        url = "https://specmatic.io/"
    }
}

tasks.getByName("beforeReleaseBuild").dependsOn("check")
tasks.getByName("afterReleaseBuild").dependsOn("dockerBuildxPublish")
tasks.getByName("afterReleaseBuild").dependsOn("publishAllPublicationsToSpecmaticPrivateRepository")
