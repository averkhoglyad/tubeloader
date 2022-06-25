package io.averkhoglyad.tubeloader.util

import java.util.*
import java.util.function.Consumer
import java.util.stream.*
import kotlin.math.ceil
import kotlin.streams.asSequence

// Stream API
inline fun <T> Stream<T>.toSet(): Set<T> = this.collect(Collectors.toSet())
inline fun IntStream.toSet(): Set<Int> = this.boxed().collect(Collectors.toSet())
inline fun LongStream.toSet(): Set<Long> = this.boxed().collect(Collectors.toSet())
inline fun DoubleStream.toSet(): Set<Double> = this.boxed().collect(Collectors.toSet())

inline fun <T> Stream<T>.toCollection(crossinline factory: () -> Collection<T>): Collection<T> = this.collect(Collectors.toCollection { factory() })
inline fun IntStream.toCollection(crossinline factory: () -> MutableCollection<Int>): Collection<Int> = this.asSequence().toCollection(factory())
inline fun LongStream.toCollection(crossinline factory: () -> MutableCollection<Long>): Collection<Long> = this.asSequence().toCollection(factory())
inline fun DoubleStream.toCollection(crossinline factory: () -> MutableCollection<Double>): Collection<Double> = this.asSequence().toCollection(factory())

//inline fun <T> Iterator<T>.stream(): Stream<T> = this.asIterable().stream()
inline fun <T> Iterable<T>.stream(): Stream<T> = this.spliterator().stream()
inline fun <T> Spliterator<T>.stream(): Stream<T> = StreamSupport.stream(this, false)

fun <T> List<Stream<out T>>.concat(): Stream<out T> {
    if (this.isEmpty()) {
        return Stream.empty()
    }
    var result: Stream<out T> = this[0]
    for (i in 1 until this.size) {
        result += this[i]
    }
    return result
}

operator fun <T> Stream<out T>.plus(other: Stream<out T>): Stream<T> = Stream.concat(this, other)

fun <T> Stream<T>.batched(bufferSize: Int): Stream<List<T>> {
    val spliterator = BatchedSpliterator(this.spliterator(), bufferSize)
    val result: Stream<List<T>> = StreamSupport.stream(spliterator, false)
    result.onClose { this.close() }
    return result
}

private class BatchedSpliterator<T>(private val root: Spliterator<T>, private val bufferSize: Int): Spliterator<List<T>> {

    override fun tryAdvance(action: Consumer<in List<T>>): Boolean {
        val buffer = ArrayList<T>(bufferSize)
        while (buffer.size < bufferSize && root.tryAdvance { buffer.add(it) });
        if (buffer.isEmpty()) return false
        action.accept(buffer)
        return true
    }

    override fun trySplit(): Spliterator<List<T>>? {
        return null
    }

    override fun estimateSize(): Long {
        val rootSize = root.estimateSize()
        return if (rootSize == Long.MAX_VALUE) Long.MAX_VALUE else ceil(rootSize / bufferSize.toDouble()).toLong()
    }

    override fun getExactSizeIfKnown(): Long {
        val rootExactSize = root.exactSizeIfKnown
        return if (rootExactSize < 0) -1 else ceil(rootExactSize / bufferSize.toDouble()).toLong()
    }

    override fun characteristics(): Int {
        return root.characteristics() and (Spliterator.ORDERED or Spliterator.SIZED) or Spliterator.NONNULL
    }

}
