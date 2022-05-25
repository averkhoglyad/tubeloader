package io.averkhoglyad.tuber.util

val logger by log4j("io.averkhoglyad.tuber.util")

inline fun <reified T> quite(fn: () -> T?): T? {
    return try {
        fn()
    } catch (e: Exception) {
        logger.warn("Exception was suppressed:", e)
        null
    }
}
