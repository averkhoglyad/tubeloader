package io.averkhogliad.tubeloader.config

class ClasspathConfigSource(
    private val resourceName: String,
    private val classLoader: ClassLoader = Thread.currentThread().contextClassLoader,
) : ConfigSource {

    override fun load(): Config? {
        val stream = classLoader.getResourceAsStream(resourceName) ?: return null
        return TomlConfig.fromStream(stream)
    }
}
