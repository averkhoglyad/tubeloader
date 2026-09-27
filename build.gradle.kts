plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

group = "io.averkhogliad"
version = "0.0.1"

allprojects {
    repositories {
        mavenCentral()
    }
}
