# TubeLoader

Desktop download manager for video and audio from multiple sources.

## What it is

TubeLoader downloads video and audio from several hosting sites, YouTube and Rutube at minimum.
The target quality is separate video and audio streams muxed into a single file with FFmpeg, so a
download is not limited to the progressive formats a site serves. The user pastes a URL and the
source is routed from that URL, with no manual source picking. New sources are added behind the
`SourceAdapter` contract without touching the core, and the GUI is a thin client over a headless
core: commands in, events out.

## Goals

- High-quality downloads: video-only + audio-only + mux.
- YouTube and Rutube out of the box.
- Extensibility: a new source is configuration plus, optionally, a custom extractor.
- Replaceable loader implementations: yt-dlp, native scrapers, and future alternatives behind the
  same port.
- Low friction for the user: no manual cookie copying, no Python or Node.js to install.
- Resilience to source changes: backend tools are updated independently of an app release.
- Cross-platform: Windows, Linux, macOS.

## Non-goals

Plugin marketplace, "any site" support as a product, a server mode, an in-house YouTube extractor
or JS interpreter in the core, private videos (public and unlisted only), accounts and sync.

## License

Apache License 2.0 — see [LICENSE](LICENSE).

Distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
implied.
