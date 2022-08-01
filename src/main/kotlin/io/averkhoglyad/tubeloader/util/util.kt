package io.averkhoglyad.tubeloader.util

val logger by log4j("io.averkhoglyad.tubeloader.util")

inline fun <reified T> quite(fn: () -> T?): T? {
    return try {
        fn()
    } catch (e: Exception) {
        logger.warn("Exception was suppressed:", e)
        null
    }
}

fun String.toTitleCase(): String {
    val sb: StringBuilder = StringBuilder(this.length)
    var newWord = true
    this.forEach { char ->
        if (newWord) {
            sb.append(char.uppercaseChar())
        } else {
            sb.append(char.lowercaseChar())
        }
        newWord = char.isWhitespace()
    }
    return sb.toString()
}
