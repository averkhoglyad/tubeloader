package io.averkhoglyad.tubeloader

import ws.schild.jave.Encoder
import ws.schild.jave.MultimediaObject
import ws.schild.jave.encode.AudioAttributes
import ws.schild.jave.encode.EncodingAttributes
import ws.schild.jave.encode.VideoAttributes
import ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator
import java.io.File
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import kotlin.io.path.outputStream
import kotlin.sequences.forEach

fun main() {
//    // clip
//    val source = "E:\\tmp\\jmeter\\vid_720p.mp4"
//    val res = "E:\\tmp\\jmeter\\res.mp4"
//    // $ ffmpeg -i input.mp4 -ss 00:00:00 -to 00:01:21 -c:v copy -c:a copy output2.mp4
//    // $ ffmpeg -i input.mp4 -ss 00:05:10 -to 00:15:30 -c:v copy -c:a copy output2.mp4
//    val cmd = "${DefaultFFMPEGLocator().executablePath} -i $source -ss 00:07:09 -to 00:15:12 -c copy $res"
//    // $ ffmpeg -i input_0.mp4 -i input_1.mp4 -c copy -map 0:v:0 -map 1:a:0 -shortest 0.mp4
////    val cmd = "${DefaultFFMPEGLocator().executablePath} -i $source -ss 00:00:00 -to 00:01:21 -i $source -ss 00:07:07 -to 00:15:12 -c copy -map 0:v:0 -map 1:a:0 -shortest $res"
//
////    val cmd = "${DefaultFFMPEGLocator().executablePath} -f concat -i E:\\tmp\\jmeter\\sources.txt -c copy  $res"
//    println(cmd)
//    Runtime.getRuntime().exec(cmd)

    val multimediaObjects = listOf(MultimediaObject(File("E:\\tmp\\jmeter\\0.mp4")), MultimediaObject(File("E:\\tmp\\jmeter\\1.mp4")))
    val encodingAttributes = EncodingAttributes()
        .apply {
            setAudioAttributes(AudioAttributes().apply {
                setCodec(AudioAttributes.DIRECT_STREAM_COPY)
            })
            setVideoAttributes(VideoAttributes().apply {
                setCodec(VideoAttributes.DIRECT_STREAM_COPY)
            })
        }
    Encoder().encode(multimediaObjects, File("E:\\tmp\\jmeter\\res.mp4"), encodingAttributes)

    // $ ffmpeg -i input_0.mp4 -i input_1.mp4 -c copy -map 0:v:0 -map 1:a:0 -shortest 0.mp4
    val cmd = "${DefaultFFMPEGLocator().executablePath} -i E:\\tmp\\jmeter\\0.mp4 -i E:\\tmp\\jmeter\\1.mp4 -c copy -map 0:v:0 -map 1:a:0 -shortest E:\\tmp\\jmeter\\res.mp4"
    println(cmd)
    Runtime.getRuntime().exec(cmd)
}