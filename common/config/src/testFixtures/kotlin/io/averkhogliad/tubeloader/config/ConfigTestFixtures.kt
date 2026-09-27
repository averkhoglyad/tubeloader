package io.averkhogliad.tubeloader.config

fun mapConfig(vararg pairs: Pair<String, String>): Config =
    object : Config {
        private val map = pairs.toMap()

        override val keys: Set<String> = map.keys

        override fun getOrNull(path: String): String? = map[path]

        override fun getTableOrNull(path: String): Map<String, Any>? {
            val prefix = "$path."
            return map
                .filterKeys { it.startsWith(prefix) }
                .mapKeys { it.key.removePrefix(prefix) }
        }
    }
