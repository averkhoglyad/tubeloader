import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.21"
    id("org.openjfx.javafxplugin") version "0.0.10"
    id("io.spring.dependency-management") version "1.0.1.RELEASE"
    application
}

group = "io.averkhoglyad"
version = "1.0-SNAPSHOT"

val targetJvmVersion = JavaVersion.VERSION_17.toString()

javafx {
    version = targetJvmVersion
    modules("javafx.controls", "javafx.graphics", "javafx.swing")
}

application {
    mainClass.set("net.averkhoglyad.grex.arrow.MainKt")
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencyManagement {
    imports {
        mavenBom("org.apache.logging.log4j:log4j-bom:2.17.2")
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-javafx:1.6.1")

    implementation("com.github.sealedtx:java-youtube-downloader:3.0.2")

    implementation("no.tornado:tornadofx:1.7.20")
    implementation("no.tornado:tornadofx-controlsfx:0.1.1")
    implementation("org.controlsfx:controlsfx:11.1.0")

    implementation("org.apache.logging.log4j:log4j-api")
    implementation("org.apache.logging.log4j:log4j-jul")

    runtimeOnly("org.apache.logging.log4j:log4j-core")
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j-impl")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = targetJvmVersion
}

tasks.withType<JavaCompile> {
    sourceCompatibility = targetJvmVersion
    targetCompatibility = targetJvmVersion
}