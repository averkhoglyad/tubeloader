package io.averkhogliad.tubeloader.core

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.string
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

private val videoIds = Arb.string(1..12)
private val videoUrls = Arb.string(1..24)

private class FacadeWorld(
    val adapter: FakeSourceAdapter,
    val mediaTool: FakeMediaTool,
    val facade: CoreFacade,
    private val snapshots: List<Map<TaskId, DownloadState>>,
) {
    fun observedStates(taskId: TaskId): List<DownloadState> = snapshots.mapNotNull { it[taskId] }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun TestScope.facadeWorld(
    adapter: FakeSourceAdapter = FakeSourceAdapter(),
    mediaTool: FakeMediaTool = FakeMediaTool(),
): FacadeWorld {
    val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
    val facade = CoreFacade(listOf(adapter), mediaTool, scope)
    val snapshots = mutableListOf<Map<TaskId, DownloadState>>()
    scope.launch { facade.downloads.collect { snapshots += it } }
    return FacadeWorld(adapter, mediaTool, facade, snapshots)
}

class CoreFacadeTest : FreeSpec({

    "parseUrl" - {
        "returns Supported with the id when the adapter claims the url" {
            runTest {
                // given
                val videoId = videoIds.next()
                val world = facadeWorld()
                world.adapter.onParseUrl = { ParseUrlResult.Supported(videoId) }

                // when
                val actual = world.facade.parseUrl(videoUrls.next())

                // then
                actual shouldBe ParseUrlResult.Supported(videoId)
            }
        }

        "returns Unsupported when no adapter claims the url" {
            runTest {
                // given
                val world = facadeWorld()
                world.adapter.onParseUrl = { ParseUrlResult.Unsupported }

                // when
                val actual = world.facade.parseUrl(videoUrls.next())

                // then
                actual shouldBe ParseUrlResult.Unsupported
            }
        }

        "returns NotFound when the adapter recognizes the source but not the video" {
            runTest {
                // given
                val world = facadeWorld()
                world.adapter.onParseUrl = { ParseUrlResult.NotFound }

                // when
                val actual = world.facade.parseUrl(videoUrls.next())

                // then
                actual shouldBe ParseUrlResult.NotFound
            }
        }
    }

    "loadMeta" - {
        "returns metadata from the adapter" {
            runTest {
                // given
                val meta = Arb.videoMetas().next()
                val world = facadeWorld()
                world.adapter.onLoadMeta = { LoadMetaResult.Found(meta) }

                // when
                val actual = world.facade.loadMeta(meta.id)

                // then
                actual shouldBe LoadMetaResult.Found(meta)
            }
        }

        "returns NotFound when the adapter has no such video" {
            runTest {
                // given
                val world = facadeWorld()
                world.adapter.onLoadMeta = { LoadMetaResult.NotFound }

                // when
                val actual = world.facade.loadMeta(videoIds.next())

                // then
                actual shouldBe LoadMetaResult.NotFound
            }
        }
    }

    "enqueue" - {
        "walks the download from Queued through Downloading to Completed" {
            runTest {
                // given
                val videoId = videoIds.next()
                val world = facadeWorld()
                world.adapter.onParseUrl = { ParseUrlResult.Supported(videoId) }
                world.facade.parseUrl(videoUrls.next())
                val release = CompletableDeferred<Unit>()
                world.adapter.onDownload = { release.await() }
                val request = Arb.downloadRequests(ids = Arb.constant(videoId)).next()

                // when
                val taskId = world.facade.enqueue(request)
                val statesWhileDownloading = world.observedStates(taskId)
                release.complete(Unit)

                // then
                statesWhileDownloading shouldBe listOf(
                    DownloadState.Queued,
                    DownloadState.Downloading(Progress.Indeterminate),
                )
                world.observedStates(taskId).last() shouldBe DownloadState.Completed
            }
        }

        "passes the request down to the adapter" {
            runTest {
                // given
                val videoId = videoIds.next()
                val world = facadeWorld()
                world.adapter.onParseUrl = { ParseUrlResult.Supported(videoId) }
                world.facade.parseUrl(videoUrls.next())
                val request = Arb.downloadRequests(ids = Arb.constant(videoId)).next()

                // when
                world.facade.enqueue(request)

                // then
                world.adapter.downloaded.single() shouldBe request
            }
        }

        "initializes the media tool once for several downloads" {
            runTest {
                // given
                val videoId = videoIds.next()
                val world = facadeWorld()
                world.adapter.onParseUrl = { ParseUrlResult.Supported(videoId) }
                world.facade.parseUrl(videoUrls.next())
                val request = Arb.downloadRequests(ids = Arb.constant(videoId)).next()

                // when
                val first = world.facade.enqueue(request)
                val second = world.facade.enqueue(request)

                // then
                world.mediaTool.initializeCalls shouldBe 1
                world.facade.downloads.value[first] shouldBe DownloadState.Completed
                world.facade.downloads.value[second] shouldBe DownloadState.Completed
            }
        }

        "ends up Failed when the adapter throws" {
            runTest {
                // given
                val videoId = videoIds.next()
                val world = facadeWorld()
                world.adapter.onParseUrl = { ParseUrlResult.Supported(videoId) }
                world.facade.parseUrl(videoUrls.next())
                world.adapter.onDownload = { error("adapter broke") }
                val request = Arb.downloadRequests(ids = Arb.constant(videoId)).next()

                // when
                val taskId = world.facade.enqueue(request)

                // then
                val failed = world.facade.downloads.value[taskId] as DownloadState.Failed
                failed.error.message shouldBe "adapter broke"
            }
        }
    }

    "cancel" - {
        "ends up Cancelled when the task is cancelled while downloading" {
            runTest {
                // given
                val videoId = videoIds.next()
                val world = facadeWorld()
                world.adapter.onParseUrl = { ParseUrlResult.Supported(videoId) }
                world.facade.parseUrl(videoUrls.next())
                world.adapter.onDownload = { CompletableDeferred<Unit>().await() }
                val taskId = world.facade.enqueue(
                    Arb.downloadRequests(ids = Arb.constant(videoId)).next(),
                )

                // when
                world.facade.cancel(taskId)

                // then
                world.facade.downloads.value[taskId] shouldBe DownloadState.Cancelled
            }
        }

        "leaves the other task of the same source untouched" {
            runTest {
                // given
                val videoId = videoIds.next()
                val world = facadeWorld()
                world.adapter.onParseUrl = { ParseUrlResult.Supported(videoId) }
                world.facade.parseUrl(videoUrls.next())
                val firstStarted = CompletableDeferred<Unit>()
                var downloads = 0
                world.adapter.onDownload = {
                    if (downloads++ == 0) {
                        firstStarted.complete(Unit)
                        CompletableDeferred<Unit>().await()
                    }
                }
                val cancelled = world.facade.enqueue(
                    Arb.downloadRequests(ids = Arb.constant(videoId)).next(),
                )
                firstStarted.await()

                // when
                val survivor = world.facade.enqueue(
                    Arb.downloadRequests(ids = Arb.constant(videoId)).next(),
                )
                world.facade.cancel(cancelled)

                // then
                world.facade.downloads.value[cancelled] shouldBe DownloadState.Cancelled
                world.facade.downloads.value[survivor] shouldBe DownloadState.Completed
            }
        }
    }
})
