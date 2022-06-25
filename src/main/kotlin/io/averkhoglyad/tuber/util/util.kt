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

fun String.toTitleCase(): String {
    val sb: StringBuilder = StringBuilder(this.length)
    var newWord = true
    this.forEachIndexed { index, char ->
        if (newWord) {
            sb.append(char.uppercaseChar())
        } else {
            sb.append(char.lowercaseChar())
        }
        newWord = char.isWhitespace()
    }
    return sb.toString()
}
