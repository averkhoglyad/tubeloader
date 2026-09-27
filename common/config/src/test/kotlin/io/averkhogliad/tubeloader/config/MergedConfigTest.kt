package io.averkhogliad.tubeloader.config

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class MergedConfigTest : FreeSpec({

    "getOrNull" - {
        "later source overrides earlier one" {
            // given
            val first = mapConfig("a" to "1", "b" to "2")
            val second = mapConfig("b" to "3")

            // when
            val config = MergedConfig(first, second)

            // then
            config.getOrNull("a") shouldBe "1"
            config.getOrNull("b") shouldBe "3"
        }

        "later source does not erase keys it lacks" {
            // given
            val config = MergedConfig(
                mapConfig("a" to "1"),
                mapConfig("b" to "2"),
            )

            // when
            val actual = config.getOrNull("a")

            // then
            actual shouldBe "1"
        }

        "returns null when no source has the key" {
            // given
            val config = MergedConfig(emptyList())

            // when
            val actual = config.getOrNull("missing")

            // then
            actual shouldBe null
        }
    }

    "getTableOrNull" - {
        "merges nested tables key by key" {
            // given
            val first = mapConfig(
                "download.max" to "2",
                "download.host" to "rutube",
            )
            val second = mapConfig("download.max" to "5")

            // when
            val config = MergedConfig(first, second)
            val table = config.getTableOrNull("download")

            // then
            table shouldBe mapOf(
                "max" to "5",
                "host" to "rutube",
            )
        }

        "table from a later source does not erase sibling keys of earlier source" {
            // given
            val first = mapConfig("download.max" to "2")
            val second = mapConfig("other.enabled" to "true")

            // when
            val config = MergedConfig(first, second)
            val download = config.getTableOrNull("download")

            // then
            download shouldBe mapOf("max" to "2")
        }
    }

    "keys" - {
        "unions keys of all sources" {
            // given
            val config = MergedConfig(
                mapConfig("a" to "1"),
                mapConfig("b" to "2"),
            )

            // when
            val actual = config.keys

            // then
            actual shouldBe setOf("a", "b")
        }
    }
})
