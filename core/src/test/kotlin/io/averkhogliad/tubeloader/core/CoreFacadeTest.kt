package io.averkhogliad.tubeloader.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
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
                world.adapters.single().onDownload = { _, _ -> release.await() }
                val request = Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next()

                // when
                val taskId = world.facade.enqueue(resolved, request)
                val statesWhileDownloading = world.observedStates(taskId)
                release.complete(Unit)

                // then
                statesWhileDownloading shouldBe listOf(
                    DownloadState(DownloadStatus.Queued),
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

        "initializes the media tool once for several downloads" {
            runTest {
                // given
                val videoId = videoIds.next()
                val world = facadeWorld()
                world.adapters.single().onFind = { FindResult.Found(videoId) }
                val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                val firstRequest = Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next()
                val secondRequest = Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next()

                // when
                val first = world.facade.enqueue(resolved, firstRequest)
                val second = world.facade.enqueue(resolved, secondRequest)

                // then
                world.mediaTool.initializeCalls shouldBe 1
                world.facade.downloads.value[first] shouldBe DownloadState(DownloadStatus.Completed)
                world.facade.downloads.value[second] shouldBe DownloadState(DownloadStatus.Completed)
            }
        }

        "ends up Failed when the adapter throws" {
            runTest {
                // given
                val videoId = videoIds.next()
                val world = facadeWorld()
                world.adapters.single().onFind = { FindResult.Found(videoId) }
                world.adapters.single().onDownload = { _, _ -> error("adapter broke") }
                val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                val request = Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next()

                // when
                val taskId = world.facade.enqueue(resolved, request)

                // then
                val failed = world.facade.downloads.value.getValue(taskId).status as DownloadStatus.Failed
                failed.error.message shouldBe "adapter broke"
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

            "deletes the partial file when the adapter throws" {
                runTest {
                    // given
                    val videoId = videoIds.next()
                    val world = facadeWorld()
                    world.adapters.single().onFind = { FindResult.Found(videoId) }
                    world.adapters.single().onDownload = { _, _ -> error("adapter broke") }
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
                    world.adapters.single().onDownload = { _, _ -> release.await() }
                    val dir = Files.createTempDirectory(tempDir, "staging")
                    val request = Arb.downloadRequests(dir, ids = Arb.constant(videoId)).next()
                    val target = request.targetPath
                    val taskId = world.facade.enqueue(resolved, request)

                    // when
                    world.facade.cancel(taskId)

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
                        release.await()
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
                        release.await()
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
                        release.await()
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
                        release.await()
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
                        release.await()
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
                        release.await()
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
                        release.await()
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
                        release.await()
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
        "ends up Cancelled when the task is cancelled while downloading" {
            runTest {
                // given
                val videoId = videoIds.next()
                val world = facadeWorld()
                world.adapters.single().onFind = { FindResult.Found(videoId) }
                val resolved = world.facade.findByUrl(inputs.next()) as ResolveResult.Resolved
                world.adapters.single().onDownload = { _, _ -> CompletableDeferred<Unit>().await() }
                val taskId = world.facade.enqueue(
                    resolved,
                Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next(),
                )

                // when
                world.facade.cancel(taskId)

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
                val firstStarted = CompletableDeferred<Unit>()
                var downloads = 0
                world.adapters.single().onDownload = { _, _ ->
                    if (downloads++ == 0) {
                        firstStarted.complete(Unit)
                        CompletableDeferred<Unit>().await()
                    }
                }
                val cancelled = world.facade.enqueue(
                    resolved,
                Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next(),
                )
                firstStarted.await()

                // when
                val survivor = world.facade.enqueue(
                    resolved,
                Arb.downloadRequests(tempDir, ids = Arb.constant(videoId)).next(),
                )
                world.facade.cancel(cancelled)

                // then
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
    val mediaTool: FakeMediaTool,
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
    mediaTool: FakeMediaTool = FakeMediaTool(),
    initialConfig: AppConfig = AppConfig(),
    taskIdGenerator: TaskIdGenerator = RandomTaskIdGenerator,
): FacadeWorld {
    val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
    val facade = CoreFacade(adapters, mediaTool, initialConfig, scope, taskIdGenerator)
    val snapshots = mutableListOf<Map<TaskId, DownloadState>>()
    scope.launch { facade.downloads.collect { snapshots += it } }
    return FacadeWorld(adapters, mediaTool, facade, snapshots)
}

private fun leftoverFilesIn(dir: Path, expected: Path): List<Path> =
    Files.newDirectoryStream(dir).use { entries -> entries.filter { it != expected } }
