import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.nio.file.Files
import java.nio.file.Paths

group = "io.averkhoglyad"
version = "1.0-SNAPSHOT"

val targetJvmVersion = JavaVersion.VERSION_17.toString()

plugins {
    application
    kotlin("jvm") version "1.6.21"
    id("org.openjfx.javafxplugin") version "0.0.12"
    id("io.spring.dependency-management") version "1.0.1.RELEASE"
//    id("org.beryx.jlink") version "2.25.0"
    id("org.panteleyev.jpackageplugin") version "1.3.1"
//    id("com.xcporter.jpkg") version "0.0.8"
}

val compileKotlin: KotlinCompile by tasks
val compileJava: JavaCompile by tasks
compileJava.destinationDirectory.set(compileKotlin.destinationDirectory.get())

tasks.test {
    useJUnitPlatform()
}

tasks.compileJava {
    sourceCompatibility = targetJvmVersion
    targetCompatibility = targetJvmVersion
}

tasks.compileKotlin {
    kotlinOptions {
        jvmTarget = targetJvmVersion
    }
}

val ffmpegbin : String?  by project

javafx {
    version = targetJvmVersion
    modules("javafx.controls", "javafx.graphics", "javafx.swing")
}

task("copyDependencies", Copy::class) {
    from(configurations.runtimeClasspath).into("$buildDir/jars")
}

task("copyJar", Copy::class) {
    from(tasks.jar).into("$buildDir/jars")
}

task("deleteDist", Copy::class) {
    from(tasks.jar).into("$buildDir/jars")
    delete("$buildDir/dist")
}

tasks.jpackage {
    dependsOn("build", "copyDependencies", "copyJar", "deleteDist")

    input  = "$buildDir/jars"
    destination = "$buildDir/dist"

    appName = "TubeLoader"
    vendor = "a.v.verkhoglyad"
    appVersion = "1"
    copyright = "Copyright (c) 2022 a.v.verkhoglyad"

    mainJar = tasks.jar.get().archiveFileName.get()
    mainClass = "io.averkhoglyad.tubeloader.MainKt"

    javaOptions = listOf("-Dfile.encoding=UTF-8")

    type = org.panteleyev.jpackage.ImageType.APP_IMAGE

    windows {
        winConsole = false
        icon = "logo.ico"
    }
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencyManagement {
    imports {
        mavenBom("org.apache.logging.log4j:log4j-bom:2.18.0")
        mavenBom("org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.6.4")
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
    
//    implementation("ws.schild:jave-nativebin-${ffmpegbin}:3.3.1")
    implementation("ws.schild:jave-nativebin-win64:3.3.1")
//    implementation("ws.schild:jave-nativebin-osx64:3.3.1")
//    implementation("ws.schild:jave-nativebin-osxm1:3.3.1")
//    implementation("ws.schild:jave-nativebin-linux64:3.3.1")
//    implementation("ws.schild:jave-nativebin-linux-arm64:3.3.1")

    // UI
//    (listOf("javafx-base") + (javafx.modules.map { it.replace(".", "-") }))
//        .map {"org.openjfx:$it:${javafx.version}"}
//        .flatMap { listOf("$it:win", "$it:linux", "$it:mac") }
//        .forEach { runtimeOnly(it) }

    implementation("no.tornado:tornadofx:1.7.20") {
        exclude("org.jetbrains.kotlin")
    }
    implementation("no.tornado:tornadofx-controlsfx:0.1.1")
    implementation("org.controlsfx:controlsfx:11.1.1")

    implementation("org.sejda.imageio:webp-imageio:0.1.6")

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
