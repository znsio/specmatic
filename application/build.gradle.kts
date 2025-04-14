plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "1.9.25"
}

dependencies {
    implementation("io.netty:netty-codec-http:4.1.118.Final")
    implementation("joda-time:joda-time:2.13.1")
    implementation("net.minidev:json-smart:2.5.2")

    implementation("com.arakelian:java-jq:2.0.0")
    testImplementation("com.arakelian:java-jq:2.0.0")

    implementation("org.assertj:assertj-core:3.27.3")
    implementation("org.junit.jupiter:junit-jupiter-api:5.11.4")

    implementation("info.picocli:picocli-spring-boot-starter:4.7.6") {
        exclude(group = "ch.qos.logback", module = "logback-classic")
        exclude(group = "org.springframework.boot", module = "spring-boot")
        exclude(group = "org.springframework.boot", module = "spring-boot-starter")
        exclude(group = "org.springframework.boot", module = "spring-boot-autoconfigure")
    }
    implementation("org.springframework.boot:spring-boot-starter:3.3.5") {
        exclude(group = "org.springframework", module = "spring-core")
        exclude(group = "ch.qos.logback", module = "logback-classic")
        exclude(group = "ch.qos.logback", module = "logback-core")
    }
    implementation("org.springframework.boot:spring-boot-autoconfigure:3.4.1")
    implementation("org.springframework:spring-core:6.1.14")
    implementation("info.picocli:picocli:4.7.6")
    implementation("io.ktor:ktor-client-core-jvm:2.3.13")
    implementation("io.ktor:ktor-network-tls:2.3.13")
    implementation("io.ktor:ktor-network-tls-certificates:2.3.13")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.3")

    implementation("org.junit.platform:junit-platform-launcher:1.11.4")
    implementation("org.junit.platform:junit-platform-reporting:1.11.4")

    implementation("org.eclipse.jgit:org.eclipse.jgit:7.0.0.202409031743-r")
    implementation("org.eclipse.jgit:org.eclipse.jgit.ssh.apache:7.0.0.202409031743-r")

    implementation("org.slf4j:slf4j-nop:2.0.17")

    implementation("org.apache.ant:ant-junit:1.10.15")

    implementation(project(":core"))
    implementation(project(":junit5-support"))

    implementation("io.ktor:ktor-client-cio:2.3.13")
    implementation("io.swagger.parser.v3:swagger-parser:2.1.24") {
        exclude(group = "org.mozilla", module = "rhino")
    }
    implementation("org.mozilla:rhino:1.7.15")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.6.3")

    implementation("org.jetbrains.kotlin:kotlin-reflect")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")

    testImplementation("org.springframework.boot:spring-boot-starter-test:3.4.2") {
        exclude(group = "ch.qos.logback", module = "logback-classic")
        exclude(module = "mockito-core")
    }
    testImplementation("io.mockk:mockk:1.13.11")
    testImplementation("com.ninja-squad:springmockk:3.1.1") {
        exclude(group = "ch.qos.logback", module = "logback-classic")
    }
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("com.ginsberg:junit5-system-exit:2.0.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.11.4")
    testImplementation("io.kotest:kotest-assertions-core-jvm:5.9.1")

}

tasks.test {
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        val junit5SystemExit =
            configurations.testRuntimeClasspath.get().files.find { it.name.contains("junit5-system-exit") }

        listOf("-javaagent:$junit5SystemExit")
    })
}
