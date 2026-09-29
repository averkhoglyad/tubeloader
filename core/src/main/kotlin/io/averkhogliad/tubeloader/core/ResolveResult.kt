package io.averkhogliad.tubeloader.core

sealed interface ResolveResult {
    data class Resolved(val ref: VideoRef) : ResolveResult

    data object Unsupported : ResolveResult

    data object NotFound : ResolveResult
}
