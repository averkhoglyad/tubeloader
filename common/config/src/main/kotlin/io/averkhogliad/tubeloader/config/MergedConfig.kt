package io.averkhogliad.tubeloader.config

class MergedConfig(
    private val sources: List<Config>,
) : Config {

    constructor(vararg sources: Config) : this(sources.toList())

    override val keys: Set<String> =
        sources.flatMapTo(mutableSetOf()) { it.keys }

    override fun getOrNull(path: String): String? {
        for (source in sources.asReversed()) {
            val value = source.getOrNull(path)
            if (value != null) return value
        }
        return null
    }

    override fun getTableOrNull(path: String): Map<String, Any>? =
        sources.fold(null as Map<String, Any>?) { merged, source ->
            val table = source.getTableOrNull(path) ?: return@fold merged
            merged?.merge(table) ?: table
        }

    private fun Map<String, Any>.merge(other: Map<String, Any>): Map<String, Any> =
        buildMap {
            putAll(this@merge)
            for ((key, theirs) in other) {
                val mine = this@merge[key]
                this[key] = if (mine is Map<*, *> && mine.keys.all { it is String } &&
                    theirs is Map<*, *> && theirs.keys.all { it is String }
                ) {
                    @Suppress("UNCHECKED_CAST")
                    (mine as Map<String, Any>).merge(@Suppress("UNCHECKED_CAST") (theirs as Map<String, Any>))
                } else {
                    theirs
                }
            }
        }
}
