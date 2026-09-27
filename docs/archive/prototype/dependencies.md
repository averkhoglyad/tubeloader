# Prototype dependency list

Snapshot of the prototype's Gradle build (`build.gradle.kts`) taken before the prototype was torn
down. Kept as a reference for future adapter specs: it records which libraries the working Rutube
and muxing code was written against.

- Source commit: `43ebd4d`
- Build plugins: `application`, `kotlin("jvm") 1.9.25`, `kotlin("plugin.serialization") 1.9.25`,
  `org.openjfx.javafxplugin 0.0.13`, `io.spring.dependency-management 1.1.0`,
  `org.panteleyev.jpackageplugin 1.5.2`
- `targetJvmVersion`: `17`
- BOM imports: `org.apache.logging.log4j:log4j-bom:2.24.1`,
  `org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.9.0`,
  `org.jetbrains.kotlinx:kotlinx-serialization-bom:1.6.3`

## Declared dependencies

```
implementation(kotlin("stdlib-jdk8"))
implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-javafx")
implementation("com.github.sealedtx:java-youtube-downloader:3.3.1")
implementation("ws.schild:jave-core:3.5.0")
implementation("ws.schild:jave-nativebin-win64:3.5.0")
implementation("no.tornado:tornadofx:1.7.20")  // with exclude("org.jetbrains.kotlin")
implementation("no.tornado:tornadofx-controlsfx:0.1.1")
implementation("org.controlsfx:controlsfx:11.2.1")
implementation("org.sejda.imageio:webp-imageio:0.1.6")
implementation("org.picocontainer:picocontainer:2.15")
implementation("org.apache.logging.log4j:log4j-api")
implementation("org.apache.logging.log4j:log4j-jul")
runtimeOnly("org.apache.logging.log4j:log4j-core")
runtimeOnly("org.apache.logging.log4j:log4j-slf4j-impl")
testImplementation(kotlin("test"))
```

Comment-only entries from the original file (never resolved by the build):

```
implementation(kotlin("reflect"))
implementation("ws.schild:jave-nativebin-osx64:3.5.0")
implementation("ws.schild:jave-nativebin-osxm1:3.5.0")
implementation("ws.schild:jave-nativebin-linux64:3.5.0")
implementation("ws.schild:jave-nativebin-linux-arm64:3.5.0")
```

## Repositories

```
mavenCentral()
maven("https://jitpack.io")
```

`jitpack.io` was needed only for `com.github.sealedtx:java-youtube-downloader`; the archived
`encode.kt` and `YoutubeVideoService.kt` use `ws.schild:jave-*` (ffmpeg wrapper) from Maven Central.

## Nature of the archived sources

The three `.kt` files are read-only references, not compilable units: they reference prototype classes
(`data.DownloadOption`, `data.VideoDetails`, `util.log4j`, `util.quietly`) that were deleted with the
prototype and are not archived.
