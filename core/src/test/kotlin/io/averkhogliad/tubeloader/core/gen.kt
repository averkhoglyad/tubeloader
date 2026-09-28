package io.averkhogliad.tubeloader.core

import io.kotest.property.Arb
import io.kotest.property.Gen
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun Arb.Companion.qualities(
    ids: Gen<String> = Arb.string(1..8),
    kinds: Gen<MediaKind> = Arb.enum(),
    labels: Gen<String> = Arb.string(1..12),
): Arb<Quality> = Arb.bind(ids, kinds, labels, ::Quality)

fun Arb.Companion.videoMetas(
    ids: Gen<String> = Arb.string(1..8),
    titles: Gen<String> = Arb.string(1..16),
    authors: Gen<String> = Arb.string(1..16),
    durations: Gen<Duration> = Arb.long(0L..10_000L).map { it.seconds },
    thumbnails: Gen<String?> = Arb.string(1..16).orNull(),
    qualities: Gen<List<Quality>> = Arb.list(Arb.qualities(), 1..3),
): Arb<VideoMeta> = Arb.bind(ids, titles, authors, durations, thumbnails, qualities, ::VideoMeta)

fun Arb.Companion.relativePaths(
    names: Arb<String> = Arb.string(1..10, Codepoint.alphanumeric()),
): Arb<Path> = names.map { Path.of("$it.mp4") }

fun Arb.Companion.downloadRequests(
    targetDirPath: Path,
    ids: Gen<String> = Arb.string(1..12),
    qualities: Gen<Quality> = Arb.qualities(),
    targetPaths: Arb<Path> = Arb.relativePaths(),
): Arb<DownloadRequest> = Arb.bind(
    ids,
    qualities,
    targetPaths.map { targetDirPath.resolve(it) },
    ::DownloadRequest,
)

fun Arb.Companion.absoluteProgresses(
    totals: Arb<Long> = Arb.long(1L..1_000_000L),
): Arb<SourceProgress.Absolute> = totals.flatMap { total ->
    Arb.long(0L..total).map { processed -> SourceProgress.Absolute(processed, total) }
}

fun Arb.Companion.fractions(
    ratios: Arb<Double> = Arb.double(0.0..1.0),
): Arb<SourceProgress.Fraction> = ratios.map { SourceProgress.Fraction(it) }
