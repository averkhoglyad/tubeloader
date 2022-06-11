package io.averkhoglyad.tuber.layout.view

import com.github.kiulian.downloader.model.videos.VideoInfo
import io.averkhoglyad.tuber.layout.fragment.VideoCardFragment
import tornadofx.View
import tornadofx.label
import tornadofx.listview
import tornadofx.observableListOf


class VideosListView : View() {

    private val videos = observableListOf<VideoInfo>()

    override val root = listview(videos) {
        placeholder = label("No videos")
        cellFragment(VideoCardFragment::class)
    }

    fun addVideo(video: VideoInfo) {
        var indexOfVideo = videos.indexOfFirst { it.details().videoId() == video.details().videoId() }
        if (indexOfVideo < 0) {
            videos.add(video)
            indexOfVideo = videos.lastIndex
        }
        root.scrollTo(indexOfVideo)
    }
}