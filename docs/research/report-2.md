# Архитектура приложения для скачивания видео

**Версия:** 1.0
**Статус:** draft / for review
**Область:** десктопное приложение (GUI/TUI), скачивание видео и аудио с YouTube и других источников

---

## 1. Назначение и цели

### 1.1. Назначение

Десктопное приложение для скачивания видео и аудио с YouTube (public и unlisted) и других источников (Rutube и др.) с объединением потоков через FFmpeg и возможностью расширения на новые площадки без изменения ядра.

### 1.2. Цели

- Скачивание видео в высоком качестве (video-only + audio-only + merge).
- Поддержка как минимум YouTube и Rutube из коробки.
- Расширяемость: новый источник — конфигурация + опциональный кастомный экстрактор.
- Заменяемость backend-реализаций: yt-dlp, кастомные экстракторы, будущие альтернативы.
- Минимальное трение для пользователя: без ручного копирования cookies, без установки Python/Node.js.
- Устойчивость к изменениям YouTube: быстрое обновление backend-инструментов.
- Кроссплатформенность: Windows, Linux, macOS.

### 1.3. Вне scope

- Private videos (только public + unlisted).
- Аккаунты, синхронизация, облако.
- Серверная часть.
- Собственный экстрактор YouTube (используется yt-dlp).
- Собственный JS-интерпретатор.

### 1.4. Ключевые архитектурные решения

| Решение | Обоснование |
|---------|-------------|
| yt-dlp как целевой backend для YouTube | Единственная библиотека с полноценной поддержкой PO Token, n-sig, impersonation, обновляется в течение дней после изменений YouTube |
| subprocess-интеграция вместо встраивания библиотеки | Не требует Python на машине пользователя, изоляция, возможность быстро обновлять бинарник |
| Порты и адаптеры | Заменяемость backend-реализаций без изменения домена и GUI |
| Декларативные источники | Добавление нового сайта без перекомпиляции ядра |
| Fallback-цепочки экстракторов | Устойчивость к поломкам отдельных backends |
| Portable бинарники | Пользователь ничего не устанавливает |
| Cookies как опция, не как обязательство | Public + unlisted не требуют cookies; cookies — fallback для anti-bot |

---

## 2. Архитектурные принципы

1. **Слоистость.** Domain → Application → Infrastructure → Presentation. Зависимости направлены внутрь.
2. **Порты и адаптеры.** Domain определяет интерфейсы, инфраструктура их реализует. GUI не знает о yt-dlp.
3. **Единый pipeline.** Все источники проходят через один pipeline. Различия — в конфигурации.
4. **Заменяемость backend.** Каждый экстрактор реализует единый порт. yt-dlp — одна из реализаций, не единственная.
5. **Асинхронность на границах.** I/O не блокирует GUI.
6. **Изоляция внешних инструментов.** yt-dlp, FFmpeg, Node.js, WebView — заменяемые компоненты.
7. **Декларативность.** Источники описываются конфигурацией, а не кодом.
8. **Управляемое усложнение.** Cookies, PO Token, WebView добавляются слоями.

---

## 3. Слоистая архитектура

```
┌──────────────────────────────────────────────────────────────┐
│                     Presentation Layer                        │
│  GUI / TUI: окна, формы, прогресс, настройки, история         │
│  Не знает о yt-dlp, FFmpeg, subprocess                        │
└───────────────────────────┬──────────────────────────────────┘
                            │ Commands / Queries / Events
┌───────────────────────────▼──────────────────────────────────┐
│                     Application Layer                         │
│  Use cases: GetVideoInfo, ListFormats, StartDownload, ...     │
│  Оркестрация pipeline, fallback-логика, управление очередью   │
└───────────────────────────┬──────────────────────────────────┘
                            │ Domain interfaces (ports)
┌───────────────────────────▼──────────────────────────────────┐
│                       Domain Layer                            │
│  Модели, правила выбора формата, состояния задач              │
│  Порты: Extractor, CookieProvider, PoTokenProvider, ...       │
│  Чистая логика без I/O                                        │
└───────────────────────────┬──────────────────────────────────┘
                            │ Ports implemented by adapters
┌───────────────────────────▼──────────────────────────────────┐
│                   Infrastructure Layer                        │
│  YtDlpExtractor, RutubeNativeExtractor, FfmpegAdapter,        │
│  CookieProvider, PoTokenProvider, UpdateService, Storage      │
└───────────────────────────┬──────────────────────────────────┘
                            │
┌───────────────────────────▼──────────────────────────────────┐
│                    External Tools                             │
│  yt-dlp | FFmpeg | Node.js + bgutil (optional) | WebView      │
└──────────────────────────────────────────────────────────────┘
```

---

## 4. Домен

### 4.1. Модели

```
VideoInfo
├── id, url, sourceId
├── title, author, duration
├── publishDate, thumbnailUrl, description
└── formats: List<Format>

Format
├── formatId, container
├── resolution, fps
├── videoCodec, audioCodec, bitrate
├── fileSize
└── kind: Progressive | VideoOnly | AudioOnly

DownloadTask
├── id, url, sourceId
├── videoInfo, selectedFormat
├── state: TaskState
├── progress: DownloadProgress
├── outputPath
└── error: ExtractionError?

DownloadProgress
├── taskId, stage
├── percent, downloadedBytes, totalBytes
├── speed, eta
├── backend: ExtractorId
└── message: String?

TaskState = Queued | FetchingInfo | Downloading | Merging | PostProcessing | Completed | Failed | Cancelled
```

### 4.2. Порты (интерфейсы)

```
interface Extractor {
    val id: ExtractorId
    fun supports(sourceId: SourceId): Boolean
    fun getInfo(url: String, ctx: DownloadContext): VideoInfo
    fun listFormats(url: String, ctx: DownloadContext): List<Format>
    fun download(req: DownloadRequest, ctx: DownloadContext): Flow<DownloadProgress>
    fun cancel(taskId: String)
    fun healthCheck(): HealthStatus
}

interface CookieProvider {
    fun getCookies(sourceId: SourceId): CookieJar?
    fun invalidate(sourceId: SourceId)
    fun requiresRefresh(sourceId: SourceId): Boolean
}

interface PoTokenProvider {
    fun isAvailable(): Boolean
    fun getBaseUrl(): String?
    fun start()
    fun stop()
}

interface UpdateService {
    fun checkForUpdates(component: ComponentId): UpdateInfo?
    fun downloadAndInstall(update: UpdateInfo): Result<Unit>
    fun currentVersion(component: ComponentId): String
}

interface SettingsRepository { ... }
interface HistoryRepository { ... }
interface SecureStorage { ... }
```

---

## 5. Источники и экстракторы

### 5.1. Ключевое разделение

- **Source** — логическая площадка (YouTube, Rutube). Декларативное описание.
- **Extractor** — механизм извлечения и скачивания. Реализация порта.
- **Backend chain** — порядок fallback экстракторов для источника.

Один источник может использовать несколько экстракторов. Один экстрактор (yt-dlp) может обслуживать много источников.

### 5.2. SourceDescriptor

```
SourceDescriptor
├── id: SourceId
├── displayName: String
├── urlPatterns: List<Regex>
├── capabilities: SourceCapabilities
├── backendChain: List<ExtractorId>
├── formatSelectionRules: FormatRules
├── authStrategy: AuthStrategy
└── postProcessingHooks: List<Hook>
```

### 5.3. SourceCapabilities

```
SourceCapabilities
├── supportsAdaptiveStreams, supportsProgressive
├── supportsHls, supportsDash
├── supportsSubtitles, supportsPlaylists, supportsChannels
├── supportsSearch, supportsLiveStreams
├── supportsMultipleAudioTracks
├── requiresCookies, supportsCookies
├── requiresPoToken, requiresSignatureDecipher
├── requiresImpersonation, requiresCustomMerge
```

### 5.4. AuthStrategy

```
AuthStrategy =
  | None                       // cookies и PO Token не нужны
  | CookiesOnly                // нужны cookies
  | PoTokenOnly                // нужен PO Token
  | CookiesAndPoToken          // оба обязательны
  | CookiesAndPoTokenOptional  // пробуем без, при ошибке — с
```

### 5.5. SourceRegistry

```
SourceRegistry
├── register(descriptor)
├── resolve(url): SourceDescriptor?   // по urlPatterns
├── all(): List<SourceDescriptor>
└── generic(): SourceDescriptor       // fallback для неизвестных URL
```

При поступлении URL:
1. `resolve(url)` перебирает patterns.
2. Если найден — возвращается дескриптор.
3. Если не найден — Generic Source (yt-dlp с generic-экстрактором).
4. Если Generic не справляется — ошибка «источник не поддерживается».

### 5.6. Целевая конфигурация источников

**YouTube (целевой, yt-dlp):**
```yaml
- id: youtube
  displayName: YouTube
  urlPatterns:
    - "(youtube\\.com|youtu\\.be)/"
  capabilities:
    supportsAdaptiveStreams: true
    supportsSubtitles: true
    supportsPlaylists: true
    supportsChannels: true
    supportsLiveStreams: true
    requiresPoToken: true
    supportsCookies: true
  backendChain: [ytdlp]
  authStrategy: CookiesAndPoTokenOptional
  formatSelectionRules:
    preferContainer: mp4
    preferH264: true
```

**Rutube (yt-dlp первично, кастомный fallback):**
```yaml
- id: rutube
  displayName: Rutube
  urlPatterns:
    - "rutube\\.ru/"
  capabilities:
    supportsAdaptiveStreams: true
    supportsHls: true
    supportsSubtitles: true
    supportsPlaylists: true
    supportsChannels: true
    requiresPoToken: false
    supportsCookies: false
  backendChain: [ytdlp, rutube-native]
  authStrategy: None
  formatSelectionRules:
    preferHls: true
    preferContainer: mp4
```

**Generic (всё остальное через yt-dlp):**
```yaml
- id: generic
  displayName: "Other sites (via yt-dlp)"
  urlPatterns: []
  backendChain: [ytdlp]
  authStrategy: CookiesAndPoTokenOptional
```

---

## 6. Pipeline загрузки

```
[1] URL → SourceRegistry.resolve → SourceDescriptor
[2] Application Layer формирует DownloadContext:
       - cookies (по authStrategy)
       - PO Token base URL (если requiresPoToken)
       - прокси, пути к бинарникам, outputDir, шаблон имени
[3] Выбор backend из source.backendChain
[4] Extractor.getInfo(url, ctx) → VideoInfo
[5] Domain format selection по source.formatSelectionRules
[6] Extractor.download(req, ctx) → Flow<DownloadProgress>
[7] Merge через FFmpeg (автоматически yt-dlp или вручную)
[8] Post-processing: source hooks + общие
[9] Сохранение, история, уведомление GUI
```

### 6.1. Fallback-логика

```
fun download(url, source, ctx):
    for backend in source.backendChain:
        try:
            return backend.download(url, ctx)
        catch e:
            if e is UnsupportedUrl or e is ExtractorBroken:
                if not isLast(backend):
                    log("backend $backend failed, trying next")
                    continue
            if e is AuthRequired or e is AntiBotDetected:
                // не fallback, а применение auth strategy
                ctx = applyAuthStrategy(ctx, source)
                retry(backend)
            if e is NetworkError:
                retryWithBackoff(backend)
            throw e
```

**Fallback — только на `UnsupportedUrl` и `ExtractorBroken`.** На `AuthRequired`, `AntiBotDetected`, `NetworkError` — другие стратегии (auth, retry). Это предотвращает молчаливую потерю качества.

### 6.2. Классификация ошибок

```
sealed class ExtractionError
├── UnsupportedUrl(sourceId, url)              // → fallback
├── ExtractorBroken(sourceId, backendId, ...)  // → fallback
├── FormatUnavailable(sourceId, format)
├── AuthRequired(sourceId, authType)           // → cookies/PO Token
├── AntiBotDetected(sourceId, backendId)       // → cookies/PO Token
├── NetworkError(...)                          // → retry
├── DiskError(...)
└── Unknown(...)
```

---

## 7. Аутентификация и обход anti-bot

### 7.1. Стратегия по уровням

| Уровень | Механизм | Когда применяется | Зависимости |
|---------|----------|-------------------|-------------|
| 0 | Без cookies и PO Token | По умолчанию для public + unlisted | Нет |
| 1 | Cookies из браузера | При 403 / bot detection | Нет |
| 2 | WebView + cookies | Если `--cookies-from-browser` не сработал | Chromium (JCEF/KCEF/Tauri) |
| 3 | PO Token (bgutil HTTP) | Если cookies недостаточно | Portable Node.js |
| 4 | Impersonation | Редко, при TLS-фингерпринт блокировках | Python + curl_cffi (не рекомендуется) |

Уровни применяются по возрастанию. Для public + unlisted уровни 0–2 покрывают большинство случаев. Уровень 3 — опционален.

### 7.2. PO Token: bgutil HTTP mode

```
App/
├── node/node.exe              # portable Node.js
├── bgutil/
│   ├── main.js
│   └── node_modules/
└── ...
```

Lifecycle:
1. При старте приложения (если PO Token включён) — запуск bgutil-сервера как subprocess.
2. Healthcheck на `http://127.0.0.1:<port>`.
3. yt-dlp вызывается с `--extractor-args "youtube:getpot_bgutil_baseurl=http://127.0.0.1:<port>"`.
4. При выходе — сервер останавливается.

Порт выбирается динамически. Node.js — portable, установка не требуется.

### 7.3. Хранение cookies

- Никогда не логировать содержимое.
- Windows: DPAPI.
- macOS: Keychain.
- Linux: Secret Service API (libsecret / GNOME Keyring / KWallet).
- Признак истечения: 403 / bot detection → предложить refresh.

---

## 8. Application Layer: use cases

```
GetVideoInfo(url): VideoInfo
ListFormats(url): List<Format>
StartDownload(request): TaskId
CancelDownload(taskId)
PauseDownload(taskId)
ResumeDownload(taskId)
RetryDownload(taskId)
GetDownloadHistory(): List<DownloadTask>
UpdateComponents()          // yt-dlp, bgutil
GetSettings() / UpdateSettings()
```

Application Layer:
- оркестрирует pipeline;
- управляет очередью (ограничение одновременных загрузок);
- координирует fallback и auth strategy;
- persist состояние для восстановления после перезапуска.

---

## 9. Infrastructure Layer

### 9.1. YtDlpExtractor

- Формирует аргументы yt-dlp.
- Запускает subprocess, читает stdout/stderr.
- Парсит прогресс через `--newline --progress-template`.
- Парсит метаданные через `--dump-json` / `-J`.
- Обрабатывает коды возврата, таймауты.
- Управляет путями к бинарникам.
- Поддерживает cookies, PO Token, прокси, impersonation через аргументы.

### 9.2. RutubeNativeExtractor

- Реализация порта `Extractor`.
- Внутри — существующий прототип: Rutube API, HLS/DASH, FFmpeg.
- Используется как fallback, если yt-dlp не справляется.
- Тестируется изолированно.

### 9.3. FfmpegAdapter

- Merge video-only + audio-only.
- Конвертация, встраивание субтитров, метаданных, обложек.
- Обычно вызывается yt-dlp автоматически через `--ffmpeg-location`.

### 9.4. CookieProvider

Реализации:
- `NullCookieProvider` — cookies не используются.
- `BrowserCookieProvider` — `--cookies-from-browser`.
- `WebViewCookieProvider` — JCEF/KCEF/Tauri WebView, логин, извлечение cookies.
- `ManualCookieProvider` — импорт `cookies.txt` (для продвинутых).

### 9.5. PoTokenProvider

- `NullPoTokenProvider` — PO Token не используется.
- `BgutilHttpPoTokenProvider` — HTTP-клиент к bgutil-серверу, управление lifecycle.

### 9.6. UpdateService

- Проверка версий yt-dlp, bgutil.
- Скачивание новых бинарников.
- Атомарная замена (скачать во временный файл → заменить → откат при ошибке).
- Периодичность: раз в день / неделю, настраиваемо.

### 9.7. Storage

- `SettingsRepository` — настройки приложения.
- `HistoryRepository` — история загрузок.
- `SecureStorage` — cookies, токены.
- `TaskStateStore` — состояние очереди для восстановления.

---

## 10. Presentation Layer

### 10.1. Экраны

- Главное окно: URL, кнопка «Скачать», выбор формата.
- Очередь загрузок: список задач, прогресс, управление.
- История: завершённые загрузки, повтор.
- Настройки: папка, шаблон имени, прокси, cookies, PO Token, обновления.
- Диалог cookies: выбор браузера или WebView-логин.
- Уведомления: завершение, ошибки, обновления.

### 10.2. Взаимодействие

- Вызывает use cases из Application Layer.
- Подписывается на события (прогресс, статус, ошибки).
- Не парсит вывод yt-dlp, не знает о subprocess.

### 10.3. События для GUI

```
VideoInfoLoaded(taskId, info)
FormatsLoaded(taskId, formats)
ProgressUpdated(taskId, progress)
TaskStateChanged(taskId, state)
ErrorOccurred(taskId, error)
UpdateAvailable(component, version)
AuthRequired(sourceId, authType)
```

Троттлинг прогресса: не чаще 10–20 раз в секунду.

---

## 11. Расширение: добавление нового источника

**Сценарий: добавить Vimeo.**

1. Проверить поддержку в yt-dlp. Если да — добавить `SourceDescriptor` с `backendChain: [ytdlp]` и нужными capabilities.
2. Если yt-dlp не справляется — написать `VimeoNativeExtractor`, реализующий `Extractor`, добавить в `backendChain`.
3. Зарегистрировать в `SourceRegistry`.
4. GUI автоматически подхватит (если использует `SourceRegistry.all()`).

**Изменений в домене, application layer, GUI — ноль.**

---

## 12. Развёртывание и упаковка

### 12.1. Структура поставки

```
App/
├── app (GUI binary)
├── bin/
│   ├── yt-dlp[.exe]
│   ├── ffmpeg[.exe]
│   ├── ffprobe[.exe]
│   └── node[.exe]              (опционально)
├── bgutil/                      (опционально)
│   ├── main.js
│   └── node_modules/
├── config/
│   └── sources.yaml
└── resources/
    ├── icons/
    └── locales/
```

### 12.2. Принципы

- Все бинарники portable, без установки.
- При первом запуске — копирование в writable-директорию (`%LOCALAPPDATA%`, `~/.local/share`, `Application Support`), если нужно.
- Проверка целостности бинарников (checksum).
- Автообновление через UpdateService.

### 12.3. Проблемы и решения

| Проблема | Решение |
|----------|---------|
| SmartScreen / Gatekeeper | Подпись кода или инструкция |
| Антивирусы на yt-dlp | Документирование, обращение в антивирусные вендоры |
| Размер поставки | 100–200 МБ с FFmpeg; Node.js опционален |
| Linux glibc | Standalone `yt-dlp_linux` или `yt-dlp_musl` |

---

## 13. Порядок реализации

### MVP
- yt-dlp + FFmpeg subprocess.
- Скачивание public YouTube по URL.
- Простой прогресс, без cookies.

### V1
- Выбор формата, очередь, история, настройки, шаблоны имён.
- `--cookies-from-browser` как fallback.
- Автообновление yt-dlp.

### V2
- Rutube через тот же pipeline (yt-dlp первично, кастомный fallback).
- WebView для cookies (KCEF или Tauri).
- Декларативные источники через `sources.yaml`.

### V3
- PO Token через bgutil HTTP + portable Node.js (если понадобится).
- Impersonation (опционально).

### V4
- Плейлисты, каналы, субтитры, SponsorBlock, конвертация в аудио.

Каждый этап самодостаточен. Усложнение — слоями.

---

## 14. Ключевые архитектурные инварианты

1. **GUI не знает о backend.** Замена yt-dlp на другую реализацию не требует изменений в GUI.
2. **Domain не знает о subprocess.** Тестируется без инфраструктуры.
3. **Источники декларативны.** Новый сайт — конфигурация.
4. **Fallback — только на неподдерживаемый URL и сломанный экстрактор.** Auth и network — отдельные стратегии.
5. **Cookies — опция, не обязательство.** Public + unlisted работают без них.
6. **PO Token — опционален.** Добавляется слоем при необходимости.
7. **Внешние инструменты изолированы.** Заменяемы, обновляемы независимо.
8. **Portable поставка.** Пользователь ничего не устанавливает.

---

## 15. Открытые вопросы

- Финальный стек GUI: Kotlin/Compose Desktop или Rust/Tauri.
- Формат хранения источников: YAML, JSON, Kotlin DSL.
- Стратегия обновления yt-dlp: автоматическая или с подтверждением.
- Нужен ли WebView в MVP или отложить до V2.
- Нужен ли PO Token в MVP или отложить до V3.

---

## Приложение A. Сравнение источников (YouTube vs Rutube)

| Аспект | YouTube | Rutube |
|--------|---------|--------|
| Backend | yt-dlp | yt-dlp (первично), rutube-native (fallback) |
| Adaptive streams | Да | Часто HLS/DASH |
| Anti-bot | Агрессивный, PO Token, n-sig | Менее агрессивный |
| PO Token | Часто нужен | Не нужен |
| Cookies | Для private/возрастных/anti-bot | Обычно не нужны |
| Субтитры | WebVTT/SRT, авто | WebVTT |
| Плейлисты, каналы, поиск | Да | Да |
| Live | Да | Да |
| Merge | yt-dlp + FFmpeg | yt-dlp + FFmpeg или вручную |

---

## Приложение B. Глоссарий

- **Source** — логическая площадка (YouTube, Rutube).
- **Extractor** — механизм извлечения и скачивания, реализующий порт.
- **Backend chain** — порядок fallback экстракторов для источника.
- **PO Token** — Proof of Origin Token, доказывает YouTube, что запрос от реального клиента.
- **bgutil** — сторонний HTTP-провайдер PO Token для yt-dlp.
- **n-sig** — параметр, вычисляемый JavaScript, требуется для部分 форматов.
- **jsinterp** — встроенный в yt-dlp интерпретатор JavaScript.
- **Impersonation** — подмена TLS-фингерпринта клиента.
