package io.averkhogliad.tubeloader.core

@JvmInline
value class TaskId(val value: Int) {
    override fun toString(): String = "%08x".format(value)
}
