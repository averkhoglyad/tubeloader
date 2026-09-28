package io.averkhogliad.tubeloader.core

import kotlin.random.Random

fun interface TaskIdGenerator {
    fun next(): TaskId
}

object RandomTaskIdGenerator : TaskIdGenerator {
    override fun next(): TaskId = TaskId(Random.nextInt())
}
