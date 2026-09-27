package io.averkhogliad.tubeloader.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Files
import java.nio.file.Path

class FileConfigSourceTest : FreeSpec({

    "load" - {
        "returns null when the file is absent" {
            // given
            val source = FileConfigSource(Path.of("no-such-dir", "config.toml"))

            // when
            val config = source.load()

            // then
            config.shouldBeNull()
        }

        "throws when the required file is absent" {
            // given
            val source = FileConfigSource(Path.of("no-such-dir", "config.toml"), required = true)

            // when + then
            shouldThrow<IllegalStateException> { source.load() }
        }

        "parses toml from an existing file" {
            // given
            val file = Files.createTempFile("tubeloader-config", ".toml")
            Files.writeString(file, "download.max-parallel-downloads = 2")

            // when
            val config = FileConfigSource(file).load()

            // then
            config?.getOrNull("download.max-parallel-downloads") shouldBe "2"
        }
    }
})
