package io.averkhogliad.tubeloader.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.string
import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.time.Duration.Companion.seconds

private val videoIds = Arb.string(1..12)
private val inputs = Arb.string(1..24)

class CoreFacadeTest : FreeSpec({

    val tempDir = Files.createTempDirectory("tubeloader-test")

    afterSpec {
        @OptIn(ExperimentalPathApi::class)
        tempDir.deleteRecursively()
    }

    "findByUrl" - {
        "returns Resolved with the id and source when one adapter claims the input" {
            runTest {
                // given
                val videoId = videoIds.next()
                val world = facadeWorld()
                world.adapters.single().onFind = { FindResult.Found(videoId) }

                // when
                val actual = world.facade.findByUrl(inputs.next())

                // then
                actual shouldBe ResolveResult.Resolved(world.facade.availableSources.single(), videoId)
            }
        }

        "returns Unsupported when no adapter claims the input" {
            runTest {
                // given
                val world = facadeWorld()
                world.adapters.single().onFind = { FindResult.Unsupported }

                // when
                val actual = world.facade.findByUrl(inputs.next())

                // then
                actual shouldBe ResolveResult.Unsupported
            }
        }

        "returns NotFound when the adapter recognizes the source but not the video" {
            runTest {
                // given
                val world = facadeWorld()
                world.adapters.single().onFind = { FindResult.NotFound }

                // when
                val actual = world.facade.findByUrl(inputs.next())

                // then
                actual shouldBe ResolveResult.NotFound
            }
        }

        "routes to the matching adapter among several" {
            runTest {
                // given
                val first = FakeSourceAdapter(displayName = "alpha")
                val second = FakeSourceAdapter(displayName = "beta")
                first.onFind = { FindResult.Unsupported }
                val videoId = videoIds.next()
                second.onFind = { FindResult.Found(videoId) }
                val world = facadeWorld(adapters = listOf(first, second))

                // when
                val actual = world.facade.findByUrl(inputs.next())

                // then
                actual shouldBe ResolveResult.Resolved(world.facade.availableSources[1], videoId)
            }
        }

        "throws on ambiguous match by two adapters" {
            runTest {
                // given
                val first = FakeSourceAdapter(displayName = "alpha")
                val second = FakeSourceAdapter(displayName = "beta")
                first.onFind = { FindResult.Found("id-1") }
                second.onFind = { FindResult.Found("id-2") }
                val world = facadeWorld(adapters = listOf(first, second))

                // when + then
                shouldThrow<IllegalStateException> {
                    world.facade.findByUrl(inputs.next())
                }
            }
        }

        "prefers NotFound over Unsupported when both occur" {
            runTest {
                // given
                val first = FakeSourceAdapter(displayName = "alpha")
                val second = FakeSourceAdapter(displayName = "beta")
                first.onFind = { FindResult.Unsupported }
                second.onFind = { FindResult.NotFound }
                val world = facadeWorld(adapters = listOf(first, second))

                // when
                val actual = world.facade.findByUrl(inputs.next())

                // then
                actual shouldBe ResolveResult.NotFound
            }
        }
    }

    "findById" - {
        "returns Resolved when the named adapter recognizes the id" {
            runTest {
                // given
                val first = FakeSourceAdapter(displayName = "alpha")
                val second = FakeSourceAdapter(displayName = "beta")
                first.onFind = { FindResult.Unsupported }
                val videoId = videoIds.next()
                second.onFind = { FindResult.Found(videoId) }
                val world = facadeWorld(adapters = listOf(first, second))
                val secondSource = world.facade.availableSources[1]

                // when
                val actual = world.facade.findById(secondSource.id, videoId)

                // then
                actual shouldBe ResolveResult.Resolved(secondSource, videoId)
            }
        }

        "throws on unknown source id" {
            runTest {
                // given
                val world = facadeWorld()

                // when + then
                shouldThrow<IllegalStateException> {
                    world.facade.findById(SourceId(99), videoIds.next())
                }
            }
        }
    }

    "loadMeta" - {
        "returns metadata from the resolved adapter" {
            runTest {
                // given
                val meta = Arb.videoMetas().next()
                val world = facadeWorld()
                world.adapters.single().onFind = { FindResult.Found(meta.id) }
                world.adapters.single().onLoadMeta = { LoadMetaResult.Found(meta) }
                val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved

                // when
                val actual = world.facade.loadMeta(resolved)

                // then
                actual shouldBe LoadMetaResult.Found(meta)
            }
        }

        "returns NotFound when the adapter has no such video" {
            runTest {
                // given
                val world = facadeWorld()
                world.adapters.single().onFind = { FindResult.Found(videoIds.next()) }
                world.adapters.single().onLoadMeta = { LoadMetaResult.NotFound }
                val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved

                // when
                val actual = world.facade.loadMeta(resolved)

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
                world.adapters.single().onFind = { FindResult.Found(videoId) }
                val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                val release = CompletableDeferred<Unit>()
                world.adapters.single().onDownload = { _, _ -> release.await(); DownloadResult.Success }
                val request = Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next()

                // when
                val taskId = world.facade.enqueue(resolved, request)
                val statesWhileDownloading = world.observedStates(taskId)
                release.complete(Unit)

                // then
                statesWhileDownloading shouldBe listOf(
                    DownloadState(DownloadStatus.Queued),
                    DownloadState(DownloadStatus.LoadingMeta),
                    DownloadState(DownloadStatus.Downloading),
                )
                world.observedStates(taskId).last() shouldBe DownloadState(DownloadStatus.Completed)
            }
        }

        "passes the request down to the adapter with the core-issued partial path" {
            runTest {
                // given
                val videoId = videoIds.next()
                val world = facadeWorld()
                world.adapters.single().onFind = { FindResult.Found(videoId) }
                val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                val request = Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next()

                // when
                val taskId = world.facade.enqueue(resolved, request)

                // then
                val passed = world.adapters.single().downloaded.single()
                passed.quality shouldBe request.quality
                passed.targetPath shouldNotBe request.targetPath
                passed.targetPath.parent shouldBe request.targetPath.parent
            }
        }

        "ends up Failed when the adapter returns DownloadResult.Failed" {
            runTest {
                // given
                val videoId = videoIds.next()
                val world = facadeWorld()
                world.adapters.single().onFind = { FindResult.Found(videoId) }
                world.adapters.single().onDownload = { _, _ -> DownloadResult.Failed(DownloadError.NetworkTransient) }
                val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                val request = Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next()

                // when
                val taskId = world.facade.enqueue(resolved, request)

                // then
                val failed = world.facade.downloads.value.getValue(taskId).status as DownloadStatus.Failed
                failed.error shouldBe DownloadError.NetworkTransient
            }
        }

        "error classes" - {
            "reports NotFound when the adapter reports NotFound" {
                runTest {
                    // given
                    val videoId = videoIds.next()
                    val world = facadeWorld()
                    world.adapters.single().onFind = { FindResult.Found(videoId) }
                    world.adapters.single().onDownload = { _, _ -> DownloadResult.Failed(DownloadError.NotFound) }
                    val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                    val request = Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next()

                    // when
                    val taskId = world.facade.enqueue(resolved, request)

                    // then
                    val failed = world.facade.downloads.value.getValue(taskId).status as DownloadStatus.Failed
                    failed.error shouldBe DownloadError.NotFound
                }
            }

            "reports NetworkTransient when the adapter reports NetworkTransient" {
                runTest {
                    // given
                    val videoId = videoIds.next()
                    val world = facadeWorld()
                    world.adapters.single().onFind = { FindResult.Found(videoId) }
                    world.adapters.single().onDownload = { _, _ -> DownloadResult.Failed(DownloadError.NetworkTransient) }
                    val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                    val request = Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next()

                    // when
                    val taskId = world.facade.enqueue(resolved, request)

                    // then
                    val failed = world.facade.downloads.value.getValue(taskId).status as DownloadStatus.Failed
                    failed.error shouldBe DownloadError.NetworkTransient
                }
            }

            "reports UrlExpired when the adapter reports UrlExpired" {
                runTest {
                    // given
                    val videoId = videoIds.next()
                    val world = facadeWorld()
                    world.adapters.single().onFind = { FindResult.Found(videoId) }
                    world.adapters.single().onDownload = { _, _ -> DownloadResult.Failed(DownloadError.UrlExpired) }
                    val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                    val request = Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next()

                    // when
                    val taskId = world.facade.enqueue(resolved, request)

                    // then
                    val failed = world.facade.downloads.value.getValue(taskId).status as DownloadStatus.Failed
                    failed.error shouldBe DownloadError.UrlExpired
                }
            }

            "reports ExtractorBroken when the adapter reports ExtractorBroken" {
                runTest {
                    // given
                    val videoId = videoIds.next()
                    val world = facadeWorld()
                    world.adapters.single().onFind = { FindResult.Found(videoId) }
                    world.adapters.single().onDownload = { _, _ -> DownloadResult.Failed(DownloadError.ExtractorBroken) }
                    val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                    val request = Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next()

                    // when
                    val taskId = world.facade.enqueue(resolved, request)

                    // then
                    val failed = world.facade.downloads.value.getValue(taskId).status as DownloadStatus.Failed
                    failed.error shouldBe DownloadError.ExtractorBroken
                }
            }
        }

        "fails the task with ExtractorBroken when the adapter throws" {
            runTest {
                // given
                val videoId = videoIds.next()
                val world = facadeWorld()
                world.adapters.single().onFind = { FindResult.Found(videoId) }
                val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                val dir = Files.createTempDirectory(tempDir, "staging")
                val request = Arb.downloadRequests(dir, ids = Arb.constant(videoId)).next()
                val target = request.targetPath
                val release = CompletableDeferred<Unit>()
                world.adapters.single().onDownload = { _, _ ->
                    release.await()
                    error("adapter broke")
                }

                // when
                val taskId = world.facade.enqueue(resolved, request)
                val whileDownloading = world.observedStates(taskId).map { it.status }
                release.complete(Unit)
                world.awaitState(taskId) { it.status is DownloadStatus.Failed }

                // then
                whileDownloading shouldBe listOf(
                    DownloadStatus.Queued,
                    DownloadStatus.LoadingMeta,
                    DownloadStatus.Downloading,
                )
                val failed = world.facade.downloads.value.getValue(taskId).status as DownloadStatus.Failed
                failed.error shouldBe DownloadError.ExtractorBroken
                leftoverFilesIn(target.parent, target) shouldBe emptyList()
            }
        }

        "media tool failures" - {
            "fails the task and deletes the partial when mux fails" {
                runTest {
                    // given
                    val videoId = videoIds.next()
                    val tool = FakeMediaTool()
                    tool.onMux = { _, _, _ -> Result.failure(IllegalStateException("mux failed")) }
                    val world = facadeWorld()
                    world.adapters.single().onFind = { FindResult.Found(videoId) }
                    val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                    val dir = Files.createTempDirectory(tempDir, "staging")
                    val request = Arb.downloadRequests(dir, ids = Arb.constant(videoId)).next()
                    val target = request.targetPath
                    world.adapters.single().onDownload = { download, _ ->
                        val audio = Files.createTempFile(dir, "audio", ".m4a")
                        try {
                            tool.mux(download.targetPath, audio, download.targetPath) {}.getOrThrow()
                        } finally {
                            Files.deleteIfExists(audio)
                        }
                        DownloadResult.Success
                    }

                    // when
                    val taskId = world.facade.enqueue(resolved, request)

                    // then
                    val failed = world.facade.downloads.value.getValue(taskId).status as DownloadStatus.Failed
                    failed.error shouldBe DownloadError.ExtractorBroken
                    leftoverFilesIn(target.parent, target) shouldBe emptyList()
                }
            }

            "fails the task and deletes the partial when remux fails" {
                runTest {
                    // given
                    val videoId = videoIds.next()
                    val tool = FakeMediaTool()
                    tool.onRemux = { _, _ -> Result.failure(IllegalStateException("remux failed")) }
                    val world = facadeWorld()
                    world.adapters.single().onFind = { FindResult.Found(videoId) }
                    val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                    val dir = Files.createTempDirectory(tempDir, "staging")
                    val request = Arb.downloadRequests(dir, ids = Arb.constant(videoId)).next()
                    val target = request.targetPath
                    world.adapters.single().onDownload = { download, _ ->
                        tool.remux(download.targetPath, download.targetPath) {}.getOrThrow()
                        DownloadResult.Success
                    }

                    // when
                    val taskId = world.facade.enqueue(resolved, request)

                    // then
                    val failed = world.facade.downloads.value.getValue(taskId).status as DownloadStatus.Failed
                    failed.error shouldBe DownloadError.ExtractorBroken
                    leftoverFilesIn(target.parent, target) shouldBe emptyList()
                }
            }

            "passes both tracks to mux in video-then-audio order" {
                runTest {
                    // given
                    val videoId = videoIds.next()
                    val tool = FakeMediaTool()
                    val world = facadeWorld()
                    world.adapters.single().onFind = { FindResult.Found(videoId) }
                    val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                    val dir = Files.createTempDirectory(tempDir, "staging")
                    val request = Arb.downloadRequests(dir, ids = Arb.constant(videoId)).next()
                    val audio = Files.createTempFile(dir, "audio", ".m4a")
                    Files.deleteIfExists(audio)
                    var pathGivenToAdapter: Path? = null
                    world.adapters.single().onDownload = { download, _ ->
                        pathGivenToAdapter = download.targetPath
                        tool.mux(download.targetPath, audio, download.targetPath) {}
                        DownloadResult.Success
                    }

                    // when
                    world.facade.enqueue(resolved, request)

                    // then
                    val call = tool.muxCalls.single()
                    call.video shouldBe pathGivenToAdapter
                    call.audio shouldBe audio
                    call.output shouldBe pathGivenToAdapter
                }
            }

            "passes progress from the tool through to the facade" {
                runTest {
                    // given
                    val videoId = videoIds.next()
                    val tool = FakeMediaTool()
                    val world = facadeWorld()
                    world.adapters.single().onFind = { FindResult.Found(videoId) }
                    val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                    val dir = Files.createTempDirectory(tempDir, "staging")
                    val request = Arb.downloadRequests(dir, ids = Arb.constant(videoId)).next()
                    val release = CompletableDeferred<Unit>()
                    world.adapters.single().onDownload = { download, onProgress ->
                        onProgress(SourceProgress.Fraction(0.5))
                        release.await()
                        DownloadResult.Success
                    }

                    // when
                    val taskId = world.facade.enqueue(resolved, request)
                    val during = world.facade.downloads.value.getValue(taskId)
                    release.complete(Unit)
                    world.awaitState(taskId) { it.status == DownloadStatus.Completed }

                    // then
                    during.status shouldBe DownloadStatus.Downloading
                    during.progress shouldBe Progress.Determinate(500, 1000)
                }
            }
        }

        "accepts a resolved video from findById without a url" {
            runTest {
                // given
                val videoId = videoIds.next()
                val world = facadeWorld()
                world.adapters.single().onFind = { FindResult.Found(videoId) }
                val source = world.facade.availableSources.single()
                val resolved = world.facade.findById(source.id, videoId) as ResolveResult.Resolved
                val request = Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next()

                // when
                val taskId = world.facade.enqueue(resolved, request)

                // then
                world.facade.downloads.value[taskId] shouldBe DownloadState(DownloadStatus.Completed)
            }
        }

        "task id allocation" - {
            "keeps both tasks when the generator repeats an occupied id" {
                runTest {
                    // given
                    val videoId = videoIds.next()
                    val generated = listOf(TaskId(1), TaskId(1), TaskId(2))
                    var index = 0
                    val world = facadeWorld(taskIdGenerator = TaskIdGenerator { generated[index++] })
                    world.adapters.single().onFind = { FindResult.Found(videoId) }
                    val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                    val firstRequest = Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next()
                    val secondRequest = Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next()

                    // when
                    val first = world.facade.enqueue(resolved, firstRequest)
                    val second = world.facade.enqueue(resolved, secondRequest)

                    // then
                    first shouldBe TaskId(1)
                    second shouldBe TaskId(2)
                    world.facade.downloads.value[first] shouldBe DownloadState(DownloadStatus.Completed)
                    world.facade.downloads.value[second] shouldBe DownloadState(DownloadStatus.Completed)
                }
            }

            "fails when the generator keeps returning an occupied id" {
                runTest {
                    // given
                    val videoId = videoIds.next()
                    val world = facadeWorld(taskIdGenerator = TaskIdGenerator { TaskId(1) })
                    world.adapters.single().onFind = { FindResult.Found(videoId) }
                    val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                    val firstRequest = Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next()
                    world.facade.enqueue(resolved, firstRequest)

                    // when
                    val request = Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next()
                    val failure = shouldThrow<IllegalStateException> { world.facade.enqueue(resolved, request) }

                    // then
                    failure.message shouldContain "occupied id"
                }
            }
        }

        "staging" - {
            "produces the final file atomically and leaves no partial behind" {
                runTest {
                    // given
                    val videoId = videoIds.next()
                    val world = facadeWorld()
                    world.adapters.single().onFind = { FindResult.Found(videoId) }
                    val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                    val dir = Files.createTempDirectory(tempDir, "staging")
                    val request = Arb.downloadRequests(dir, ids = Arb.constant(videoId)).next()
                    val target = request.targetPath

                    // when
                    val taskId = world.facade.enqueue(resolved, request)

                    // then
                    world.facade.downloads.value[taskId] shouldBe DownloadState(DownloadStatus.Completed)
                    Files.exists(target) shouldBe true
                    leftoverFilesIn(target.parent, target) shouldBe emptyList()
                }
            }

            "deletes the partial file when the adapter returns DownloadResult.Failed" {
                runTest {
                    // given
                    val videoId = videoIds.next()
                    val world = facadeWorld()
                    world.adapters.single().onFind = { FindResult.Found(videoId) }
                    world.adapters.single().onDownload = { _, _ -> DownloadResult.Failed(DownloadError.NetworkTransient) }
                    val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                    val dir = Files.createTempDirectory(tempDir, "staging")
                    val request = Arb.downloadRequests(dir, ids = Arb.constant(videoId)).next()
                    val target = request.targetPath

                    // when
                    val taskId = world.facade.enqueue(resolved, request)

                    // then
                    world.facade.downloads.value.getValue(taskId).status.shouldBeInstanceOf<DownloadStatus.Failed>()
                    Files.exists(target) shouldBe false
                    leftoverFilesIn(target.parent, target) shouldBe emptyList()
                }
            }

            "deletes the partial file when the task is cancelled" {
                runTest {
                    // given
                    val videoId = videoIds.next()
                    val world = facadeWorld()
                    world.adapters.single().onFind = { FindResult.Found(videoId) }
                    val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                    val release = CompletableDeferred<Unit>()
                    world.adapters.single().onDownload = { _, _ -> release.await(); DownloadResult.Success }
                    val dir = Files.createTempDirectory(tempDir, "staging")
                    val request = Arb.downloadRequests(dir, ids = Arb.constant(videoId)).next()
                    val target = request.targetPath
                    val taskId = world.facade.enqueue(resolved, request)

                    // when
                    world.facade.cancel(taskId)
                    world.awaitState(taskId) { it.status == DownloadStatus.Cancelled }

                    // then
                    world.facade.downloads.value[taskId] shouldBe DownloadState(DownloadStatus.Cancelled)
                    Files.exists(target) shouldBe false
                    leftoverFilesIn(target.parent, target) shouldBe emptyList()
                }
            }
        }

        "overwrite confirmation" - {
            "interrupts when the target file already exists before download starts" {
                runTest {
                    // given
                    val videoId = videoIds.next()
                    val world = facadeWorld()
                    world.adapters.single().onFind = { FindResult.Found(videoId) }
                    val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                    val request = Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next()
                    val target = request.targetPath
                    Files.createFile(target)

                    // when
                    val taskId = world.facade.enqueue(resolved, request)

                    // then
                    world.facade.downloads.value.getValue(taskId).status.shouldBeInstanceOf<DownloadStatus.Interrupted>()
                }
            }

            "completes after the user confirms overwrite" {
                runTest {
                    // given
                    val videoId = videoIds.next()
                    val world = facadeWorld()
                    world.adapters.single().onFind = { FindResult.Found(videoId) }
                    val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                    val request = Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next()
                    val target = request.targetPath
                    Files.createFile(target)
                    val taskId = world.facade.enqueue(resolved, request)

                    // when
                    world.awaitState(taskId) { it.status is DownloadStatus.Interrupted }
                    world.facade.confirmOverwrite(taskId, overwrite = true)

                    // then
                    world.awaitState(taskId) { it.status == DownloadStatus.Completed }
                    world.facade.downloads.value[taskId] shouldBe DownloadState(DownloadStatus.Completed)
                }
            }

            "cancels and cleans up when the user declines overwrite" {
                runTest {
                    // given
                    val videoId = videoIds.next()
                    val world = facadeWorld()
                    world.adapters.single().onFind = { FindResult.Found(videoId) }
                    val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                    val request = Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next()
                    val target = request.targetPath
                    Files.createFile(target)
                    val taskId = world.facade.enqueue(resolved, request)

                    // when
                    world.awaitState(taskId) { it.status is DownloadStatus.Interrupted }
                    world.facade.confirmOverwrite(taskId, overwrite = false)

                    // then
                    world.awaitState(taskId) { it.status == DownloadStatus.Cancelled }
                    world.facade.downloads.value[taskId] shouldBe DownloadState(DownloadStatus.Cancelled)
                }
            }
        }

        "progress" - {
            "reports Determinate for absolute source progress" {
                runTest {
                    // given
                    val videoId = videoIds.next()
                    val world = facadeWorld()
                    world.adapters.single().onFind = { FindResult.Found(videoId) }
                    val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                    val absolute = Arb.absoluteProgresses().next()
                    val reported = CompletableDeferred<Unit>()
                    val release = CompletableDeferred<Unit>()
                    world.adapters.single().onDownload = { _, onProgress ->
                        onProgress(absolute)
                        reported.complete(Unit)
                        release.await(); DownloadResult.Success
                    }

                    // when
                    val taskId = world.facade.enqueue(
                        resolved,
                        Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next(),
                    )
                    reported.await()

                    // then
                    world.facade.downloads.value[taskId] shouldBe DownloadState(
                        status = DownloadStatus.Downloading,
                        progress = Progress.Determinate(absolute.processed, absolute.total),
                    )
                    release.complete(Unit)
                }
            }

            "reports Determinate for fractional source progress" {
                runTest {
                    // given
                    val videoId = videoIds.next()
                    val world = facadeWorld()
                    world.adapters.single().onFind = { FindResult.Found(videoId) }
                    val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                    val fraction = Arb.fractions().next()
                    val reported = CompletableDeferred<Unit>()
                    val release = CompletableDeferred<Unit>()
                    world.adapters.single().onDownload = { _, onProgress ->
                        onProgress(fraction)
                        reported.complete(Unit)
                        release.await(); DownloadResult.Success
                    }

                    // when
                    val taskId = world.facade.enqueue(
                        resolved,
                        Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next(),
                    )
                    reported.await()

                    // then
                    val state = world.facade.downloads.value.getValue(taskId)
                    state.status shouldBe DownloadStatus.Downloading
                    val determinate = state.progress as Progress.Determinate
                    (determinate.current.toDouble() / determinate.total) shouldBe (fraction.ratio plusOrMinus 0.001)
                    release.complete(Unit)
                }
            }

            "reports Indeterminate when the source has no measurable progress" {
                runTest {
                    // given
                    val videoId = videoIds.next()
                    val world = facadeWorld()
                    world.adapters.single().onFind = { FindResult.Found(videoId) }
                    val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                    val reported = CompletableDeferred<Unit>()
                    val release = CompletableDeferred<Unit>()
                    world.adapters.single().onDownload = { _, onProgress ->
                        onProgress(SourceProgress.Indeterminate)
                        reported.complete(Unit)
                        release.await(); DownloadResult.Success
                    }

                    // when
                    val taskId = world.facade.enqueue(
                        resolved,
                        Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next(),
                    )
                    reported.await()

                    // then
                    world.facade.downloads.value[taskId] shouldBe DownloadState(
                        status = DownloadStatus.Downloading,
                        progress = Progress.Indeterminate,
                    )
                    release.complete(Unit)
                }
            }

            "falls back to Indeterminate when absolute progress has zero total" {
                runTest {
                    // given
                    val videoId = videoIds.next()
                    val world = facadeWorld()
                    world.adapters.single().onFind = { FindResult.Found(videoId) }
                    val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                    val processed = Arb.long(0L..1_000_000L).next()
                    val reported = CompletableDeferred<Unit>()
                    val release = CompletableDeferred<Unit>()
                    world.adapters.single().onDownload = { _, onProgress ->
                        onProgress(SourceProgress.Absolute(processed, total = 0L))
                        reported.complete(Unit)
                        release.await(); DownloadResult.Success
                    }

                    // when
                    val taskId = world.facade.enqueue(
                        resolved,
                        Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next(),
                    )
                    reported.await()

                    // then
                    world.facade.downloads.value[taskId] shouldBe DownloadState(
                        status = DownloadStatus.Downloading,
                        progress = Progress.Indeterminate,
                    )
                    release.complete(Unit)
                }
            }

            "falls back to Indeterminate when processed exceeds total" {
                runTest {
                    // given
                    val videoId = videoIds.next()
                    val world = facadeWorld()
                    world.adapters.single().onFind = { FindResult.Found(videoId) }
                    val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                    val total = Arb.long(1L..1_000_000L).next()
                    val reported = CompletableDeferred<Unit>()
                    val release = CompletableDeferred<Unit>()
                    world.adapters.single().onDownload = { _, onProgress ->
                        onProgress(SourceProgress.Absolute(total + 1, total))
                        reported.complete(Unit)
                        release.await(); DownloadResult.Success
                    }

                    // when
                    val taskId = world.facade.enqueue(
                        resolved,
                        Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next(),
                    )
                    reported.await()

                    // then
                    world.facade.downloads.value[taskId] shouldBe DownloadState(
                        status = DownloadStatus.Downloading,
                        progress = Progress.Indeterminate,
                    )
                    release.complete(Unit)
                }
            }

            "falls back to Indeterminate when the fraction is outside zero to one" {
                runTest {
                    // given
                    val videoId = videoIds.next()
                    val world = facadeWorld()
                    world.adapters.single().onFind = { FindResult.Found(videoId) }
                    val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                    val reported = CompletableDeferred<Unit>()
                    val release = CompletableDeferred<Unit>()
                    world.adapters.single().onDownload = { _, onProgress ->
                        onProgress(SourceProgress.Fraction(Double.POSITIVE_INFINITY))
                        reported.complete(Unit)
                        release.await(); DownloadResult.Success
                    }

                    // when
                    val taskId = world.facade.enqueue(
                        resolved,
                        Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next(),
                    )
                    reported.await()

                    // then
                    world.facade.downloads.value[taskId] shouldBe DownloadState(
                        status = DownloadStatus.Downloading,
                        progress = Progress.Indeterminate,
                    )
                    release.complete(Unit)
                }
            }

            "falls back to Indeterminate when a finite fraction exceeds one" {
                runTest {
                    // given
                    val videoId = videoIds.next()
                    val world = facadeWorld()
                    world.adapters.single().onFind = { FindResult.Found(videoId) }
                    val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                    val reported = CompletableDeferred<Unit>()
                    val release = CompletableDeferred<Unit>()
                    world.adapters.single().onDownload = { _, onProgress ->
                        onProgress(SourceProgress.Fraction(5.0))
                        reported.complete(Unit)
                        release.await(); DownloadResult.Success
                    }

                    // when
                    val taskId = world.facade.enqueue(
                        resolved,
                        Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next(),
                    )
                    reported.await()

                    // then
                    world.facade.downloads.value[taskId] shouldBe DownloadState(
                        status = DownloadStatus.Downloading,
                        progress = Progress.Indeterminate,
                    )
                    release.complete(Unit)
                }
            }

            "keeps the reported progress when the task interrupts for confirmation" {
                runTest {
                    // given
                    val videoId = videoIds.next()
                    val world = facadeWorld()
                    world.adapters.single().onFind = { FindResult.Found(videoId) }
                    val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                    val request = Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next()
                    val absolute = Arb.absoluteProgresses().next()
                    world.adapters.single().onDownload = { _, onProgress ->
                        onProgress(absolute)
                        Files.createFile(request.targetPath)
                        DownloadResult.Success
                    }

                    // when
                    val taskId = world.facade.enqueue(resolved, request)

                    // then
                    world.facade.downloads.value[taskId] shouldBe DownloadState(
                        status = DownloadStatus.Interrupted(PendingInteraction.OverwriteConfirmation(request.targetPath)),
                        progress = Progress.Determinate(absolute.processed, absolute.total),
                    )
                }
            }

            "updates progress without changing the status when a late callback arrives" {
                runTest {
                    // given
                    val videoId = videoIds.next()
                    val world = facadeWorld()
                    world.adapters.single().onFind = { FindResult.Found(videoId) }
                    val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                    val absolute = Arb.absoluteProgresses().next()
                    lateinit var lateProgress: (SourceProgress) -> Unit
                    val reported = CompletableDeferred<Unit>()
                    val release = CompletableDeferred<Unit>()
                    world.adapters.single().onDownload = { _, onProgress ->
                        lateProgress = onProgress
                        onProgress(absolute)
                        reported.complete(Unit)
                        release.await(); DownloadResult.Success
                    }
                    val taskId = world.facade.enqueue(
                        resolved,
                        Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next(),
                    )
                    reported.await()

                    // when
                    release.complete(Unit)
                    world.awaitState(taskId) { it.status == DownloadStatus.Completed }
                    lateProgress(SourceProgress.Fraction(0.5))

                    // then
                    val state = world.facade.downloads.value.getValue(taskId)
                    state.status shouldBe DownloadStatus.Completed
                    state.progress shouldBe Progress.Determinate(500L, 1000L)
                }
            }
        }
    }

    "cancel" - {
        "publishes Cancelling while the task is stopping and Cancelled after it stopped" {
            runTest {
                // given
                val videoId = videoIds.next()
                val world = facadeWorld()
                world.adapters.single().onFind = { FindResult.Found(videoId) }
                val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                val stopping = CompletableDeferred<Unit>()
                world.adapters.single().onDownload = { _, _ ->
                    try {
                        CompletableDeferred<Unit>().await()
                    } catch (cancelled: CancellationException) {
                        withContext(NonCancellable) { stopping.await() }
                        throw cancelled
                    }
                    DownloadResult.Success
                }
                val dir = Files.createTempDirectory(tempDir, "cancelling")
                val request = Arb.downloadRequests(dir, ids = Arb.constant(videoId)).next()
                val target = request.targetPath
                val taskId = world.facade.enqueue(resolved, request)
                world.awaitState(taskId) { it.status == DownloadStatus.Downloading }

                // when
                world.facade.cancel(taskId)
                val whileStopping = world.facade.downloads.value.getValue(taskId).status
                stopping.complete(Unit)
                world.awaitState(taskId) { it.status == DownloadStatus.Cancelled }

                // then
                whileStopping shouldBe DownloadStatus.Cancelling
                world.facade.downloads.value[taskId] shouldBe DownloadState(DownloadStatus.Cancelled)
                Files.exists(target) shouldBe false
                leftoverFilesIn(target.parent, target) shouldBe emptyList()
            }
        }

        "cancels a task that waits for the overwrite confirmation and keeps the target file" {
            runTest {
                // given
                val videoId = videoIds.next()
                val world = facadeWorld()
                world.adapters.single().onFind = { FindResult.Found(videoId) }
                val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                val dir = Files.createTempDirectory(tempDir, "cancelling-confirmation")
                val request = Arb.downloadRequests(dir, ids = Arb.constant(videoId)).next()
                val target = request.targetPath
                Files.createFile(target)
                val taskId = world.facade.enqueue(resolved, request)
                world.awaitState(taskId) { it.status is DownloadStatus.Interrupted }

                // when
                world.facade.cancel(taskId)
                world.awaitState(taskId) { it.status == DownloadStatus.Cancelled }

                // then
                world.facade.downloads.value[taskId] shouldBe DownloadState(DownloadStatus.Cancelled)
                Files.exists(target) shouldBe true
                leftoverFilesIn(target.parent, target) shouldBe emptyList()
            }
        }

        "cancels a task that reached the second overwrite confirmation after Finalizing" {
            runTest {
                // given
                val videoId = videoIds.next()
                val world = facadeWorld()
                world.adapters.single().onFind = { FindResult.Found(videoId) }
                val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                val dir = Files.createTempDirectory(tempDir, "finalizing-cancel")
                val request = Arb.downloadRequests(dir, ids = Arb.constant(videoId)).next()
                val target = request.targetPath
                world.adapters.single().onDownload = { _, _ ->
                    Files.createFile(target)
                    DownloadResult.Success
                }
                val taskId = world.facade.enqueue(resolved, request)
                world.awaitState(taskId) { it.status is DownloadStatus.Interrupted }

                // when
                world.facade.cancel(taskId)
                world.awaitState(taskId) { it.status == DownloadStatus.Cancelled }

                // then
                // the task starts with no target file, so this Interrupted is the one raised after the
                // download finished: reaching it means the Finalizing transition has been published.
                // Finalizing itself is conflated with it by the StateFlow, the two transitions have no
                // suspension point between them
                val observed = world.observedStates(taskId).map { it.status }
                observed.first() shouldBe DownloadStatus.Queued
                observed shouldContain DownloadStatus.Interrupted(PendingInteraction.OverwriteConfirmation(target))
                observed.takeLast(2) shouldBe listOf(DownloadStatus.Cancelling, DownloadStatus.Cancelled)
                world.adapters.single().downloaded.size shouldBe 1
                world.facade.downloads.value[taskId] shouldBe DownloadState(DownloadStatus.Cancelled)
                Files.exists(target) shouldBe true
                leftoverFilesIn(target.parent, target) shouldBe emptyList()
            }
        }

        "cancels a task that has not started yet and leaves nothing behind" {
            runTest {
                // given
                val videoId = videoIds.next()
                val world = facadeWorld(dispatcher = StandardTestDispatcher(testScheduler))
                world.adapters.single().onFind = { FindResult.Found(videoId) }
                val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                val dir = Files.createTempDirectory(tempDir, "cancelled-before-start")
                val request = Arb.downloadRequests(dir, ids = Arb.constant(videoId)).next()
                val target = request.targetPath
                val taskId = world.facade.enqueue(resolved, request)

                // when
                world.facade.cancel(taskId)

                // then
                world.facade.downloads.value[taskId] shouldBe DownloadState(DownloadStatus.Cancelling)
                testScheduler.advanceUntilIdle()
                world.facade.downloads.value[taskId] shouldBe DownloadState(DownloadStatus.Cancelled)
                world.adapters.single().downloaded shouldBe emptyList()
                leftoverFilesIn(target.parent, target) shouldBe emptyList()
            }
        }

        "keeps the task Completed when the cancel command arrives after the work is done" {
            runTest {
                // given
                val videoId = videoIds.next()
                val world = facadeWorld()
                world.adapters.single().onFind = { FindResult.Found(videoId) }
                val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                val taskId = world.facade.enqueue(
                    resolved,
                    Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next(),
                )
                world.facade.downloads.value[taskId] shouldBe DownloadState(DownloadStatus.Completed)

                // when
                world.facade.cancel(taskId)

                // then
                world.facade.downloads.value[taskId] shouldBe DownloadState(DownloadStatus.Completed)
            }
        }

        "does not move the file when the adapter survives the cancellation request" {
            runTest {
                // given
                val videoId = videoIds.next()
                val world = facadeWorld()
                world.adapters.single().onFind = { FindResult.Found(videoId) }
                val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                val release = CompletableDeferred<Unit>()
                world.adapters.single().onDownload = { _, _ ->
                    withContext(NonCancellable) { release.await() }
                    DownloadResult.Success
                }
                val dir = Files.createTempDirectory(tempDir, "surviving-cancel")
                val request = Arb.downloadRequests(dir, ids = Arb.constant(videoId)).next()
                val target = request.targetPath
                val taskId = world.facade.enqueue(resolved, request)
                world.awaitState(taskId) { it.status == DownloadStatus.Downloading }

                // when
                world.facade.cancel(taskId)
                release.complete(Unit)
                testScheduler.advanceUntilIdle()

                // then
                // the adapter ignored the cancellation and returned success, but the cancel command was
                // received before the move: the finished file must not appear and the task stays cancelled
                world.facade.downloads.value[taskId] shouldBe DownloadState(DownloadStatus.Cancelled)
                Files.exists(target) shouldBe false
                leftoverFilesIn(target.parent, target) shouldBe emptyList()
            }
        }

        "keeps Failed when the cancellation completes after the adapter broke" {
            runTest {
                // given
                val videoId = videoIds.next()
                val world = facadeWorld()
                world.adapters.single().onFind = { FindResult.Found(videoId) }
                val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                val release = CompletableDeferred<Unit>()
                world.adapters.single().onDownload = { _, _ ->
                    withContext(NonCancellable) { release.await() }
                    error("adapter broke")
                }
                val dir = Files.createTempDirectory(tempDir, "late-cancelled")
                val request = Arb.downloadRequests(dir, ids = Arb.constant(videoId)).next()
                val taskId = world.facade.enqueue(resolved, request)
                world.awaitState(taskId) { it.status == DownloadStatus.Downloading }

                // when
                world.facade.cancel(taskId)
                release.complete(Unit)
                testScheduler.advanceUntilIdle()

                // then
                // the adapter broke after the cancellation request, so the failure is the outcome;
                // the Cancelled of the completion handler must not overwrite it
                world.facade.downloads.value[taskId] shouldBe DownloadState(
                    status = DownloadStatus.Failed(DownloadError.ExtractorBroken),
                )
                leftoverFilesIn(request.targetPath.parent, request.targetPath) shouldBe emptyList()
            }
        }

        "keeps the task Failed when the cancel command arrives after the adapter broke" {
            runTest {
                // given
                val videoId = videoIds.next()
                val world = facadeWorld()
                world.adapters.single().onFind = { FindResult.Found(videoId) }
                world.adapters.single().onDownload = { _, _ ->
                    DownloadResult.Failed(DownloadError.NetworkTransient)
                }
                val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                val taskId = world.facade.enqueue(
                    resolved,
                    Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next(),
                )
                val broken = DownloadStatus.Failed(DownloadError.NetworkTransient)
                world.facade.downloads.value.getValue(taskId).status shouldBe broken

                // when
                world.facade.cancel(taskId)

                // then
                world.facade.downloads.value.getValue(taskId).status shouldBe broken
            }
        }

        "keeps Cancelled when the cancel command arrives again after the task stopped" {
            runTest {
                // given
                val videoId = videoIds.next()
                val world = facadeWorld()
                world.adapters.single().onFind = { FindResult.Found(videoId) }
                val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                world.adapters.single().onDownload = { _, _ ->
                    CompletableDeferred<Unit>().await(); DownloadResult.Success
                }
                val taskId = world.facade.enqueue(
                    resolved,
                    Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next(),
                )
                world.facade.cancel(taskId)
                world.awaitState(taskId) { it.status == DownloadStatus.Cancelled }

                // when
                world.facade.cancel(taskId)

                // then
                world.facade.downloads.value[taskId] shouldBe DownloadState(DownloadStatus.Cancelled)
            }
        }

        "ends up Cancelled when the task is cancelled while downloading" {
            runTest {
                // given
                val videoId = videoIds.next()
                val world = facadeWorld()
                world.adapters.single().onFind = { FindResult.Found(videoId) }
                val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                world.adapters.single().onDownload = { _, _ -> CompletableDeferred<Unit>().await(); DownloadResult.Success }
                val taskId = world.facade.enqueue(
                    resolved,
                Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next(),
                )

                // when
                world.facade.cancel(taskId)
                world.awaitState(taskId) { it.status == DownloadStatus.Cancelled }

                // then
                world.facade.downloads.value[taskId] shouldBe DownloadState(DownloadStatus.Cancelled)
            }
        }

        "leaves the other task of the same source untouched" {
            runTest {
                // given
                val videoId = videoIds.next()
                val world = facadeWorld()
                world.adapters.single().onFind = { FindResult.Found(videoId) }
                val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                val cancelledStarted = CompletableDeferred<Unit>()
                val survivorStarted = CompletableDeferred<Unit>()
                val releaseSurvivor = CompletableDeferred<Unit>()
                var downloads = 0
                world.adapters.single().onDownload = { _, _ ->
                    if (downloads++ == 0) {
                        cancelledStarted.complete(Unit)
                        CompletableDeferred<Unit>().await()
                    } else {
                        survivorStarted.complete(Unit)
                        releaseSurvivor.await()
                    }
                    DownloadResult.Success
                }
                val cancelled = world.facade.enqueue(
                    resolved,
                Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next(),
                )
                cancelledStarted.await()

                // when
                val survivor = world.facade.enqueue(
                    resolved,
                Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next(),
                )
                survivorStarted.await()
                world.facade.cancel(cancelled)
                world.awaitState(cancelled) { it.status == DownloadStatus.Cancelled }

                // then
                world.facade.downloads.value.getValue(survivor).status shouldBe DownloadStatus.Downloading
                releaseSurvivor.complete(Unit)
                world.awaitState(survivor) { it.status == DownloadStatus.Completed }
                world.facade.downloads.value[cancelled] shouldBe DownloadState(DownloadStatus.Cancelled)
                world.facade.downloads.value[survivor] shouldBe DownloadState(DownloadStatus.Completed)
            }
        }
    }

    "setConfig" - {
        "exposes the initial config on the StateFlow" {
            runTest {
                // given
                val initial = AppConfig(
                    maxParallelDownloads = 2,
                    defaultTargetDir = Path.of("D:/vid"),
                )
                val world = facadeWorld(initialConfig = initial)

                // when
                val actual = world.facade.config.value

                // then
                actual shouldBe initial
            }
        }

        "replaces the config in runtime" {
            runTest {
                // given
                val world = facadeWorld()
                val updated = AppConfig(
                    maxParallelDownloads = 5,
                    defaultTargetDir = Path.of("E:/media"),
                )

                // when
                world.facade.setConfig(updated)

                // then
                world.facade.config.value shouldBe updated
            }
        }
    }
})

private class FacadeWorld(
    val adapters: List<FakeSourceAdapter>,
    val facade: CoreFacade,
    private val snapshots: List<Map<TaskId, DownloadState>>,
) {
    fun observedStates(taskId: TaskId): List<DownloadState> = snapshots.mapNotNull { it[taskId] }

    suspend fun awaitState(taskId: TaskId, predicate: (DownloadState) -> Boolean) {
        withTimeout(5.seconds) {
            while (!predicate(facade.downloads.value[taskId] ?: return@withTimeout)) {
                kotlinx.coroutines.yield()
            }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun TestScope.facadeWorld(
    adapters: List<FakeSourceAdapter> = listOf(FakeSourceAdapter()),
    initialConfig: AppConfig = AppConfig(),
    taskIdGenerator: TaskIdGenerator = RandomTaskIdGenerator,
    dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(testScheduler),
): FacadeWorld {
    val scope = CoroutineScope(dispatcher)
    val facade = CoreFacade(adapters, initialConfig, scope, taskIdGenerator)
    val snapshots = mutableListOf<Map<TaskId, DownloadState>>()
    scope.launch { facade.downloads.collect { snapshots += it } }
    return FacadeWorld(adapters, facade, snapshots)
}

private fun leftoverFilesIn(dir: Path, expected: Path): List<Path> =
    Files.newDirectoryStream(dir).use { entries -> entries.filter { it != expected } }
