package io.averkhogliad.tubeloader.core

enum class MediaKind { Video, Audio }

data class Quality(
    val id: String,
    val kind: MediaKind,
    val label: String,
)
