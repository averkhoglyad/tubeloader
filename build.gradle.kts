import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "io.averkhoglyad"
version = "1.0-SNAPSHOT"

val targetJvmVersion = JavaVersion.VERSION_17.toString()

val ffmpegbin : String?  by project
if (ffmpegbin.isNullOrBlank()) {
    throw IllegalStateException("ffmpegbin is not defined, run gradle task with parameter -Pffmpegbin and pass codecs depends on target en, e.g. -Pffmpegbin=win64")
}

plugins {
    kotlin("jvm") version "1.7.0"
    id("org.openjfx.javafxplugin") version "0.0.13"
    id("io.spring.dependency-management") version "1.0.1.RELEASE"
    application
}

javafx {
    version = targetJvmVersion
    modules("javafx.controls", "javafx.graphics", "javafx.swing")
}

application {
    mainClass.set("io.averkhoglyad.tubeloader.MainKt")
}

distributions {
    main {
        distributionBaseName.set("${project.name}-${ffmpegbin}")
    }
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencyManagement {
    imports {
        mavenBom("org.apache.logging.log4j:log4j-bom:2.17.2")
        mavenBom("org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.6.3")
    }
}

dependencies {
    // Kotlin
    implementation(kotlin("stdlib-jdk8"))
//    implementation(kotlin("reflect"))

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-javafx")

    // Video/Audio
    implementation("com.github.sealedtx:java-youtube-downloader:3.0.2")
    implementation("ws.schild:jave-core:3.3.1")
    
    implementation("ws.schild:jave-nativebin-${ffmpegbin}:3.3.1")
//    implementation("ws.schild:jave-nativebin-win64:3.3.1")
//    implementation("ws.schild:jave-nativebin-osx64:3.3.1")
//    implementation("ws.schild:jave-nativebin-osxm1:3.3.1")
//    implementation("ws.schild:jave-nativebin-linux64:3.3.1")
//    implementation("ws.schild:jave-nativebin-linux-arm64:3.3.1")

    // UI
    implementation("no.tornado:tornadofx:1.7.20")
    implementation("no.tornado:tornadofx-controlsfx:0.1.1")
    implementation("org.controlsfx:controlsfx:11.1.1")

    // DI
    implementation("org.picocontainer:picocontainer:2.15")

    // Logging
    implementation("org.apache.logging.log4j:log4j-api")
    implementation("org.apache.logging.log4j:log4j-jul")
    runtimeOnly("org.apache.logging.log4j:log4j-core")
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j-impl")

    // Tests
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