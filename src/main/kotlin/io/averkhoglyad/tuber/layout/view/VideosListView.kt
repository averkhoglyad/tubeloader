package io.averkhoglyad.tuber.layout.view

import com.github.kiulian.downloader.model.videos.VideoInfo
import io.averkhoglyad.tuber.data.VideoDetails
import io.averkhoglyad.tuber.layout.fragment.VideoCardFragment
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