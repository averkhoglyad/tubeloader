package io.averkhogliad.tubeloader.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

class TomlConfigTest : FreeSpec({

    "fromString" - {
        "parses dotted-path sections into flat dotted keys" {
            // given
            val toml = """
                [download]
                max-parallel-downloads = 3
                [download.limits]
                per-host = 1
            """.trimIndent()

            // when
            val config = TomlConfig.fromString(toml)

            // then
            config.keys shouldBe setOf(
                "download.max-parallel-downloads",
                "download.limits.per-host",
            )
            config.getOrNull("download.max-parallel-downloads") shouldBe "3"
        }

        "returns null for missing path and for path through a scalar" {
            // given
            val config = TomlConfig.fromString("a = 1")

            // when
            val missing = config.getOrNull("nope")
            val throughScalar = config.getOrNull("a.b")

            // then
            missing shouldBe null
            throughScalar shouldBe null
        }

        "getTableOrNull returns nested maps of string leaves and maps" {
            // given
            val toml = """
                [download]
                max-parallel-downloads = 3
                enabled = true
            """.trimIndent()

            // when
            val table = TomlConfig.fromString(toml).getTableOrNull("download")

            // then
            table shouldBe mapOf(
                "max-parallel-downloads" to 3L,
                "enabled" to true,
            )
        }

        "nests dotted keys written inside a table" {
            // given
            val toml = """
                [a]
                b.c = 1
                b.d = 2
                e = 3
            """.trimIndent()

            // when
            val table = TomlConfig.fromString(toml).getTableOrNull("a")

            // then
            table shouldBe mapOf(
                "b" to mapOf("c" to 1L, "d" to 2L),
                "e" to 3L,
            )
        }

        "throws on invalid toml" {
            // when + then
            shouldThrow<Exception> {
                TomlConfig.fromString("this is [ not toml")
            }
        }

        "closes the input stream after reading" {
            // given
            val closed = AtomicBoolean(false)
            val stream = object : InputStream() {
                override fun read(): Int =
                    if (closed.get()) throw IOException("stream is closed") else -1

                override fun close() {
                    closed.set(true)
                }
            }

            // when
            TomlConfig.fromStream(stream)

            // then
            closed.get() shouldBe true
        }
    }

    "getTableOrNull" - {
        "returns empty map for a scalar path" {
            // given
            val config = TomlConfig.fromString("a = 1")

            // when
            val table = config.getTableOrNull("a")

            // then
            table shouldBe emptyMap()
            table.shouldBeInstanceOf<Map<String, Any>>()
        }
    }
})
