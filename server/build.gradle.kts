// Build configuration for the Momento API (the Gradle equivalent of a pom.xml).
//   gradle test        -> runs the JUnit test suite
//   gradle buildFatJar -> build/libs/momento-server.jar (app + all dependencies, runnable with java -jar)
plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("io.ktor.plugin") version "2.3.12"
    application
}

group = "com.momento"
version = "1.0.0"

application {
    mainClass.set("com.momento.server.ApplicationKt")
}

// Produce Java 17 bytecode, so the JAR runs on the JRE 17 used by Ansible, Docker and Kubernetes,
// whichever JDK (17 or newer) Gradle itself runs on.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Versions come from the Ktor BOM that the io.ktor.plugin applies.
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")
    implementation("io.ktor:ktor-server-status-pages-jvm")
    implementation("ch.qos.logback:logback-classic:1.5.6")

    testImplementation("io.ktor:ktor-server-test-host-jvm")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped") }
}

ktor {
    fatJar {
        archiveFileName.set("momento-server.jar")
    }
}
