package io.averkhogliad.tubeloader.core

sealed interface ResolveResult {
    data class Resolved(val source: Source, val videoId: String) : ResolveResult

    data object Unsupported : ResolveResult

    data object NotFound : ResolveResult
}
