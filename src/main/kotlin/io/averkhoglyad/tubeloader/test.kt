package io.averkhoglyad.tubeloader

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.file.Paths
import kotlin.io.path.bufferedReader
import kotlin.io.path.inputStream
import kotlin.io.path.readBytes

fun main() {
    println("---")
    Paths.get("C:\\Games\\Pole Chudes\\data.dcp")
        .inputStream()
        .bufferedReader(Charsets.ISO_8859_1)
//        .bufferedReader(Charset.forName("Windows-1251"))
        .use { input ->
            println(input.readLine())
        }
    println("---")
}