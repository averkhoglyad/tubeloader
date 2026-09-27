package io.averkhogliad.tubeloader.config

interface ConfigSource {
    fun load(): Config?
}
