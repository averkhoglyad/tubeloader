# gpui Assessment

Investigation of [gpui](https://github.com/zed-industries/zed/tree/main/crates/gpui) (Zed Industries' UI framework) as a GUI candidate for Tubeloader. Feeds wayfinder ticket `etc/issues/stack-decision/issues/01-gpui-research.md`. Each claim cites a primary source.

Context: Tubeloader core is Kotlin/JVM headless; GUI choice is between Compose Desktop (same language) and Rust+gpui (separate language, needs IPC). Win64-first, Apache-2.0.

## 1. API and paradigm

gpui is a **hybrid immediate and retained mode, GPU-accelerated UI framework** for Rust ([README](https://github.com/zed-industries/zed/blob/main/crates/gpui/README.md)).

Three "registers" ([README: The Big Picture](https://github.com/zed-industries/zed/blob/main/crates/gpui/README.md#the-big-picture)):

- **Entities** — state management. `Entity<T>` is GPUI-owned, accessed via an `Rc`-like smart pointer; used for cross-component app state. See `app::context` module.
- **Views** — declarative UI. A view is an `Entity` implementing the `Render` trait; GPUI calls `render()` on the root view each frame. Views build a tree of `elements`, styled with a tailwind-style API (`div().flex().gap_3()...`). See `div` element.
- **Elements** — low-level imperative UI for custom layout, large lists, code-editor-style rendering. See `element` module.

Entry point: `gpui_platform::application().run(|cx: &mut App| { cx.open_window(...) })` ([README: Getting Started](https://github.com/zed-industries/zed/blob/main/crates/gpui/README.md#getting-started), [gpui.rs hello world](https://gpui.rs/)).

Reactivity: render is called per-frame on the root view; entities notify observers to trigger re-render (ownership/data-flow doc: [`_ownership_and_data_flow.rs`](https://github.com/zed-industries/zed/blob/main/crates/gpui/src/_ownership_and_data_flow.rs)).

## 2. License

**Apache-2.0** ([Cargo.toml `license = "Apache-2.0"`](https://github.com/zed-industries/zed/blob/main/crates/gpui/Cargo.toml); confirmed [lib.rs](https://lib.rs/crates/gpui)).

Apache-2.0 is the same license Tubeloader itself ships under, so no compatibility question arises.

## 3. Cross-platform

| OS | Backend | Status |
|---|---|---|
| macOS | Metal (always available) | Primary, stable |
| Linux/FreeBSD | blade-graphics (Vulkan); Wayland + X11 | Supported |
| Windows | Win32 windowing + DirectWrite text; DX11/D3D | Functional, actively maturing |

Sources: [README: gpui_platform](https://github.com/zed-industries/zed/blob/main/crates/gpui/README.md#gpui_platform); [Cargo.toml platform deps](https://github.com/zed-industries/zed/blob/main/crates/gpui/Cargo.toml).

Windows "no features are required" per README, but is still receiving core fixes (2026):
- Touch input / press-and-hold not yet wired ([#64205](https://github.com/zed-industries/zed/pull/64205), open, Sep 2026).
- Mouse cursor updates ignored between cursor styles ([#64476](https://github.com/zed-industries/zed/pull/64476), open, Sep 2026).
- Mica backdrop blurs fonts ([#56382](https://github.com/zed-industries/zed/issues/56382)).
- `xwin` cross-build failure ([#62522](https://github.com/zed-industries/zed/issues/62522)).
- GPU device-loss recovery not graceful ([#23288](https://github.com/zed-industries/zed/issues/23288), `area:gpui`, `platform:linux` but affects all GPU backends).

Zed itself ships Windows builds, so Windows is usable, but it is not as battle-tested as macOS.

## 4. Ecosystem

- **Maturity:** pre-1.0. Crate published as 0.2.2 (Oct 22, 2025); ~68,185 downloads/month; used in 141 crates (129 directly) ([lib.rs](https://lib.rs/crates/gpui)).
- **Documentation:** sparse. README: "the best way to learn about these APIs is to read the Zed source code or drop a question in the Zed Discord" ([README](https://github.com/zed-industries/zed/blob/main/crates/gpui/README.md)). [gpui.rs](https://gpui.rs/) has a hello-world + example index. No API reference beyond rustdoc.
- **Third-party desktop apps (outside Zed):** all small/hobby scale:
  - [opennote](https://github.com/opennote-org/opennote) — AI notebook, 19★
  - [Binnacle](https://github.com/binnacle-app/Binnacle) — Cloudflare D1/R2/KV manager, 17★
  - [ronin](https://github.com/roninchat/ronin) — Linux AI chat, 6★
  - Various ≤3★ notes/typing/markdown apps.
- **Component library:** [gpui-kit / gpui-component](https://github.com/longbridge/gpui-kit) (Longbridge) — 14.8k★, Apache-2.0, shadcn/ui-inspired components, design guides, docs site. Ships `gpui-pre-*` snapshot crates to pin gpui versions. This is the most mature ecosystem piece.
- **Community:** Zed Discord ([zed.dev/community-links](https://zed.dev/community-links)); GitHub Discussions on the Zed repo.

## 5. Binaries / dependencies

- **Rust-only:** the crate is pure Rust. No manual native library installation on Windows (DirectWrite and Win32 accessed via the `windows` crate) ([Cargo.toml Windows deps](https://github.com/zed-industries/zed/blob/main/crates/gpui/Cargo.toml)).
- **macOS:** requires Xcode + command-line tools (Metal) ([README: macOS deps](https://github.com/zed-industries/zed/blob/main/crates/gpui/README.md#macos)).
- **Linux:** needs Wayland/X11 system libs (implicit via `blade-graphics`, `wayland-client`, `x11rb`).
- **Build:** `embed-resource` embeds a Windows manifest (`windows-manifest` feature) ([Cargo.toml](https://github.com/zed-industries/zed/blob/main/crates/gpui/Cargo.toml)). macOS build uses `bindgen`/`cbindgen`.
- **Binary size:** ~12 MB min (per [gpui-kit comparison table](https://gpui-kit.com/docs/comparison)); crate itself ~5.5 MB, 72k SLoC ([lib.rs](https://lib.rs/crates/gpui)).

## 6. API stability

- **Pre-1.0, no semver guarantees.** README: "GPUI is still in active development... is still pre-1.0. There will often be breaking changes between versions" ([README: Getting Started](https://github.com/zed-industries/zed/blob/main/crates/gpui/README.md#getting-started)).
- Only 2 real published versions: 0.1.0 (Jun 2022) → 0.2.2 (Oct 2025) ([lib.rs](https://lib.rs/crates/gpui)). Crate publishing lags behind the Zed monorepo.
- **Versioning friction:** a renovate PR bumped gpui to `v0.999999.0` (a placeholder/joke version) ([rysk-tanaka/csvr#14](https://github.com/rysk-tanaka/csvr/pull/14)), signalling dependency-tracking pain.
- **Mitigation pattern:** [gpui-kit](https://github.com/longbridge/gpui-kit) publishes `gpui-pre-*` snapshot crates to pin against specific Zed commits, because upstream does not tag releases.
- lib.rs labels the release "1 unstable release" ([lib.rs](https://lib.rs/crates/gpui)).

## 7. Comparison with alternatives

| | gpui | egui | iced | Tauri | Slint |
|---|---|---|---|---|---|
| Paradigm | hybrid imm/retained | immediate mode | Elm (retained) | web-tech (HTML/JS) | DSL (retained) |
| License | Apache-2.0 | MIT/Apache | MIT | MIT/MIT | GPL-3.0-only / commercial |
| Downloads/mo | ~68k | ~2M | ~200k | (very large) | ~191k |
| Stability | pre-1.0, breaking | 0.x, 35 breaking/66 releases | "experimental" | 2.x stable | 1.x stable |
| Windows | usable, maturing | mature | mature | mature | mature |
| Native look | custom (tailwind-style) | non-native | custom | web (any) | custom/Qt opt |
| Docs | sparse | good | book + docs | excellent | good |
| Ecosystem | small + gpui-kit (14.8k★) | largest Rust GUI | medium | largest overall | medium |

Sources: [lib.rs egui](https://lib.rs/crates/egui), [lib.rs iced](https://lib.rs/crates/iced), [lib.rs slint](https://lib.rs/crates/slint), [gpui-kit comparison](https://gpui-kit.com/docs/comparison).

- **egui:** easiest, largest Rust GUI, but non-native and breaking changes common.
- **iced:** Elm architecture, type-safe, experimental, MIT.
- **Tauri:** not a Rust-native UI — uses web technologies for the frontend; largest ecosystem but introduces JS/HTML toolchain.
- **Slint:** GPL-3.0-only or commercial; 1.x stable; DSL-based; supports embedded + desktop.

## Verdict for Tubeloader

- **License:** OK — Apache-2.0, same as Tubeloader.
- **Win64-first:** usable but still maturing; core Windows bugs being fixed in 2026.
- **API stability:** high risk — pre-1.0, no semver, breaking changes expected, version tracking requires snapshot forks.
- **IPC cost:** gpui is Rust; Tubeloader core is Kotlin/JVM. Choosing gpui adds a language boundary with no proven gpui↔JVM integration pattern (would need custom IPC, e.g., stdin/stdout JSON or sockets).
- **Ecosystem/docs:** too thin for a production app relying on it; Longbridge's gpui-kit is the only sizable contributor.

**Recommendation:** gpui is high-risk for Tubeloader. Its performance edge (Zed-grade rendering) is overkill for a download manager. The pre-1.0 instability, sparse docs, small ecosystem, Windows maturing status, and added IPC complexity outweigh the performance benefit. Compose Desktop (same language as core, stable API) is the lower-risk choice; revisit gpui after it reaches 1.0 and stabilizes Windows.
