# tubeloader

GUI tool to download videos from YouTube

## Building

Since ffmpeg codecs are used to concat video and audio files. Codecs are powered by [JAVE2 library](https://github.com/a-schild/jave2). 
Build must be executed for with parameter to define native bins depends on target env.
Pass project parameter `ffmpegbin` on task run to define native bins, e.g. `gradle distZip -Pffmpegbin=win64`. 

Some of possible values:
- win64
- linux64 
- linux-arm64
- osx64
- osxm1

Find more native bin artefacts here: [JAVE2 native bins](https://mvnrepository.com/search?q=ws.schild%3Ajave-nativebin)

## License

I hope [Apache 2.0 License](https://www.apache.org/licenses/LICENSE-2.0) must be enough for everybody.
