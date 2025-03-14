import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    `kotlin-dsl`
    id("base")
    id("io.specmatic.gradle") version ("0.0.28")
}

allprojects {
    repositories {
        mavenCentral()
        mavenLocal()
    }
}


specmatic {
    kotlinApiVersion = KotlinVersion.KOTLIN_1_9
    publishTo("specmaticPrivate", "https://maven.pkg.github.com/znsio/specmatic-private-maven-repo")

    withProject(project(":core")) {
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

    withProject(project(":junit5-support")) {
        publish {
            artifactId = "junit5-support"
            pom {
                name = "SpecmaticJUnit5Support"
                description = "Specmatic JUnit 5 Support"
                commonPomContents()
            }
        }
    }

    withProject(project(":application")) {
        iAmABigFatLibrary = true
        applicationMainClass = "application.SpecmaticApplication"
        shadowApplication()
        publish {
            artifactId = "specmatic-executable"
            pom {
                name = "Specmatic Executable"
                description = "Command-line standalone executable jar for Specmatic"
                commonPomContents()
            }
        }
    }

    withProject(rootProject) {
        dockerBuild()
    }
}

subprojects {
    tasks.withType(Test::class.java) {
        maxParallelForks = 5
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
tasks.getByName("afterReleaseBuild").dependsOn("")
