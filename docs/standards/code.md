# Код: стандарт репо

Источник истины по оформлению кода. Правила ниже применяются при проектировании шва и при
ревью, а не только при написании нового кода.

## Порядок параметров

Колбек- и лямбда-параметры идут **последними** — в методе, функции и конструкторе.

Список читается сверху вниз как «данные и зависимости, затем канал обратной связи». Колбек в
середине списка разрывает эту последовательность и заставляет читателя искать, к чему относятся
параметры после него.

Правильно:

```kotlin
class DownloadDispatcher(
    private val scope: CoroutineScope,
    private val taskIdGenerator: TaskIdGenerator = RandomTaskIdGenerator,
    private val register: (TaskId) -> Boolean,
    private val transition: (TaskId, DownloadStatus) -> Unit,
    private val onProgress: (TaskId, Progress) -> Unit,
)

suspend fun downloadVideo(
    id: String,
    quality: Quality,
    targetPath: Path,
    onProgress: (SourceProgress) -> Unit,
): DownloadResult
```

Неправильно:

```kotlin
// колбек зажат между зависимостями и дефолтом
class DownloadDispatcher(
    private val scope: CoroutineScope,
    private val register: (TaskId) -> Boolean,
    private val transition: (TaskId, DownloadStatus) -> Unit,
    private val onProgress: (TaskId, Progress) -> Unit,
    private val taskIdGenerator: TaskIdGenerator = RandomTaskIdGenerator,
)
```

**У функции с дефолтными параметрами и колбеком дефолты не должны оказаться после колбека.**
Вызов, опускающий дефолты, читался бы так, будто колбек — тоже дефолт; кроме того ломается
trailing lambda. Сначала идут параметры с дефолтами, затем колбек.

Причина правила в Kotlin: trailing lambda работает только для последнего параметра, а вынос
колбека в конец — единственный способ и сохранить его, и не разрывать список зависимостей.

## Где применяется

Швы ядра: контракт `SourceAdapter` (`core/.../SourceAdapter.kt`), порт `MediaTool`
(`core/.../MediaTool.kt`), `DownloadDispatcher` (`core/.../DownloadDispatcher.kt`). Новый шов с
колбеком проектируется по этому правилу; отклонение фиксируется в спеке фичи или в ADR.
