package io.averkhoglyad.tubeloader.layout.view

import io.averkhoglyad.tubeloader.data.VideoDetails
import io.averkhoglyad.tubeloader.layout.fragment.VideoCardFragment
import tornadofx.View
import tornadofx.label
import tornadofx.listview
import tornadofx.observableListOf


class VideosListView : View() {

    private val videos = observableListOf<VideoDetails>()

    override val root = listview(videos) {
        placeholder = label("No videos")
        cellFragment(VideoCardFragment::class)
    }

    fun addVideo(video: VideoDetails) {
        var indexOfVideo = videos.indexOfFirst { it.id == video.id }
        if (indexOfVideo < 0) {
            videos.add(video)
            indexOfVideo = videos.lastIndex
        }
        root.scrollTo(indexOfVideo)
    }
}