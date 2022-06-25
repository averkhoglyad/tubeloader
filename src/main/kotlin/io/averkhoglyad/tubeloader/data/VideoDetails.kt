package io.averkhoglyad.tubeloader.data

import kotlin.time.Duration

data class VideoDetails(val id: String,
                        val title: String,
                        val author: String,
                        val thumbnail: String?,
                        val duration: Duration,
                        val downloadOptions: List<DownloadOption>)

data class DownloadOption(val label: String,
                          val extension: String,
                          // Provider specific data
                          // TODO: Find a way to avoid casts but do not use classes/types related to any provider
                          val data: Any?)
