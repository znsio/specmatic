plugins {
    id("java-library")
    kotlin("jvm")
    kotlin("plugin.serialization") version "1.9.25"
}

dependencies {
    implementation("io.netty:netty-codec-http:4.1.118.Final")
    implementation("joda-time:joda-time:2.13.1")
    implementation("net.minidev:json-smart:2.5.2")

    implementation("com.ezylang:EvalEx:3.4.0")
    implementation("org.apache.commons:commons-lang3:3.17.0")
    implementation("io.cucumber:gherkin:22.0.0")
    implementation("io.ktor:ktor-server-netty:2.3.13")
    implementation("io.ktor:ktor-server-core:2.3.13")
    implementation("io.ktor:ktor-client-core-jvm:2.3.13")
    implementation("io.ktor:ktor-client-apache:2.3.13")
    implementation("io.ktor:ktor-server-cors:2.3.13")
    implementation("io.ktor:ktor-server-double-receive:2.3.13")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.13")
    implementation("io.ktor:ktor-serialization-jackson:2.3.13")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.3")
    implementation("com.jayway.jsonpath:json-path:2.9.0")


    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.6.3")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.25")
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.0.0.202409031743-r")
    implementation("org.eclipse.jgit:org.eclipse.jgit.ssh.apache:7.0.0.202409031743-r")
    implementation("com.fasterxml.jackson.core:jackson-core:2.18.3")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")

    implementation("com.flipkart.zjsonpatch:zjsonpatch:0.4.16")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")

    implementation("io.swagger.parser.v3:swagger-parser:2.1.24")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    implementation("com.github.mifmif:generex:1.0.2")
    implementation("dk.brics:automaton:1.12-1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.11.4")
    testImplementation("org.json:json:20250107")
    testImplementation("org.springframework:spring-web:6.1.12")
    testImplementation("io.mockk:mockk:1.13.11")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("io.ktor:ktor-client-mock-jvm:2.3.13")
    implementation("org.thymeleaf:thymeleaf:3.1.3.RELEASE")
}
