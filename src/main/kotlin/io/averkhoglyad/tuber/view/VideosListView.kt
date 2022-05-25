package io.averkhoglyad.tuber.view

import com.github.kiulian.downloader.model.videos.VideoInfo
import io.averkhoglyad.tuber.fragment.DownloadRequestEvent
import io.averkhoglyad.tuber.fragment.VideoCardFragment
import tornadofx.View
import tornadofx.listview
import tornadofx.observableListOf


class VideosListView : View() {

    private val videos = observableListOf<VideoInfo>()

    override val root = listview(videos) {
        cellFragment(VideoCardFragment::class)
    }

    fun addVideo(video: VideoInfo) {
        videos.add(0, video)
    }
}