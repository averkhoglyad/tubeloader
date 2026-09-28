## Основные форматы идентификаторов

### UUID v4
Классический 128-битный случайный идентификатор. Самый распространённый, поддерживается стандартной библиотекой почти во всех языках.

- **Плюсы:** не требует координации, универсальная поддержка, нулевая вероятность коллизии на практике
- **Минусы:** 36 символов с дефисами, не сортируется по времени, фрагментирует B-tree индексы в БД
- **Где брать:** `uuid` (npm), `java.util.UUID`, `uuid.uuid4()` (Python), `gen_random_uuid()` в PostgreSQL

### UUID v7
Стандартизован в RFC 9562 в 2024 году. Первые 48 бит — временная метка в миллисекундах, остальные 74 бит — случайность. [```1```](https://calcsi.com/en-us/blog/uuid-v4-v7-ulid)

- **Плюсы:** сортируется по времени, совместим с UUID-форматом, нативная поддержка в PostgreSQL 17+, хорошая производительность в БД
- **Минусы:** 36 символов с дефисами, новая библиотека нужна не везде
- **Библиотеки:** `uuid6` (Python: `pip install uuid6`), `uuid` v9+ (npm), нативно в PostgreSQL 18

### ULID
128 бит: 48 бит — timestamp, 80 бит — случайность. Кодируется в Crockford Base32, 26 символов без дефисов. [```2```](https://www.alldevtoolshub.com/blog/uuid-vs-ulid-vs-nanoid-vs-snowflake/)

- **Плюсы:** лексикографически сортируется, URL-безопасен, компактнее UUID
- **Минусы:** не RFC-стандарт, нужна сторонняя библиотека, раскрывает timestamp
- **Библиотеки:** `python-ulid` (Python), `ulid` (npm), `oklog/ulid` (Go)

### Snowflake ID
64-битное целое число: 41 бит — timestamp, 10 бит — machine ID, 12 бит — sequence counter. Разработан Twitter. [```3```](https://medium.com/@sinha.k/choosing-unique-identifiers-snowflake-id-vs-uuidv4-vs-uuidv7-vs-ulids-a67cd03f4934)

- **Плюсы:** самый компактный (8 байт), сортируемый, до 4096 ID/мс на узел
- **Минусы:** требует координации (назначение worker ID), раскрывает timestamp и топологию инфраструктуры
- **Библиотеки:** `callicoder/java-snowflake` (Java), `snowflake-uuid` (TypeScript), `IdGen` (.NET), `Sonyflake` (Go)

### NanoID
Короткий, настраиваемый, URL-безопасный идентификатор. По умолчанию 21 символ. [```9```](https://dev.to/gulshanaggarwal/npm-packages-to-generate-unique-ids-for-your-next-project-1p3b)

- **Плюсы:** крошечный размер (~130 байт в JS), настраиваемые длина и алфавит, криптостойкий
- **Минусы:** не сортируется по времени, без встроенной timestamp-компоненты
- **Где брать:** `nanoid` (npm — самый популярный), есть порты на Python, Go, Rust, Java, PHP

### KSUID
160 бит: 32-битный timestamp (секунды) + 128 бит случайности. Кодируется в Base62, 27 символов. Создан компанией Segment. [```11```](https://fastuuid.com/learn-about-uuids/better-alternative-to-uuid/)

- **Плюсы:** сортируемый, большое пространство идентификаторов (160 бит)
- **Минусы:** больше UUID по размеру (20 байт vs 16), не стандарт
- **Библиотеки:** `svix-ksuid` (Python), `ksuid` (npm, Go, Java)

### CUID / CUID2
Заточен на высокую уникальность в средах с высокой конкурентностью. [```8```](https://bool.dev/blog/detail/unique-id-generation)

- **Плюсы:** устойчив к коллизиям даже при высокой нагрузке, читаемый
- **Минусы:** не сортируется по времени
- **Где брать:** `cuid` (npm)

### Sqids
Превращает числа в короткие уникальные строки. [```7```](https://github.com/topics/unique-id-generator)

- **Плюсы:** URL-безопасные, можно декодировать обратно в число, есть порты почти на все языки
- **Минусы:** не подходит для распределённой генерации — это кодирование чисел, а не их создание
- **Библиотеки:** `sqids` (JavaScript, Python, Go, PHP, Ruby, Java, C++, Rust, Swift, Kotlin — десятки портов)

## Сравнительная таблица

| Формат | Размер | Сортируемый | Координация | Длина строки | БД-дружелюбность |
|--------|--------|-------------|------------|--------------|-------------------|
| UUID v4 | 128 бит | Нет | Нет | 36 | Плохая (фрагментация) |
| UUID v7 | 128 бит | Да | Нет | 36 | Отличная |
| ULID | 128 бит | Да | Нет | 26 | Отличная |
| Snowflake | 64 бита | Да | Да (worker ID) | до 19 цифр | Отличная |
| TSID | 64 бита | Да | Да (node ID) | 13 | Отличная |
| NanoID | настраиваемый | Нет | Нет | 21 (по умолч.) | Зависит от длины |
| KSUID | 160 бит | Да | Нет | 27 | Хорошая |

 [```13```](https://www.guidsgenerator.com/wiki/uuid-vs-others)[```15```](https://createuuid.com/articles/uuid-alternatives)

## Что выбрать

- **Новая таблица с высокой записью (2026):** UUIDv7 или ULID — сортируемые, без координации [```2```](https://www.alldevtoolshub.com/blog/uuid-vs-ulid-vs-nanoid-vs-snowflake/)
- **Максимальная совместимость:** UUID v4 — поддерживается везде
- **Компактность и 64-битное хранение:** Snowflake, TSID или Sonyflake
- **Короткие URL-безопасные ID:** NanoID
- **Логирование и event-sourcing:** KSUID (большое пространство + timestamp) [```14```](https://pkgsearch.dev/uuid/alternatives)
- **Скрыть timestamp (приватность):** UUID v4 или NanoID [```5```](https://aiappbox.tech/en/guides/uuid-vs-ulid-guide)

## Главная проблема

**32 бита — это очень мало** для уникального сортируемого по времени ID. Для сравнения: Snowflake использует 64 бита, ULID — 128. Готовых библиотек для `int32` с time-based сортировкой практически нет, потому что компромисс слишком жёсткий. [```1```](https://www.javathinking.com/blog/how-to-generate-unique-id-in-java-integer/)

Поэтому решение — **кастомный генератор**. Вот рабочая реализация на Kotlin и разбор компромиссов.

## Распределение бит

Java `int` — знаковый, поэтому безопасный диапазон положительных значений: `0 .. 2³¹−1` (~2.1 млрд). Если в БД используется `INT UNSIGNED` (MySQL), можно задействовать все 32 бита.

Два практичных варианта:

| Параметр | Вариант A (31 бит, знаковый) | Вариант B (32 бита, unsigned) |
|----------|---------------------------|-------------------------------|
| Timestamp | 23 бита, секунды | 24 бита, секунды |
| Срок жизни | ~97 дней от epoch | ~194 дня от epoch |
| Sequence | 8 бит = 256 ID/сек | 8 бит = 256 ID/сек |
| Node ID | нет (один узел) | нет (один узел) |

> Можно перераспределить: пожертвовать sequence ради node bits для распределённой генерации, но тогда throughput падает до десятков ID в секунду на узел.

## Реализация на Kotlin

```kotlin
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/**
 * 32-битный time-sorted генератор уникальных ID.
 *
 * Структура (31 бит, знаковый int):
 *   [31..9] — 23 бита timestamp в секундах от CUSTOM_EPOCH
 *   [8..0]  — 8 бит sequence (0..255)
 *
 * Ограничения:
 *   - Один узел (нет распределённой координации)
 *   - Максимум 256 ID в секунду
 *   - Срок жизни ~97 дней от CUSTOM_EPOCH
 *   - При переполнении sequence ждёт следующую секунду
 */
class Int32TimeSortedIdGenerator(
    private val customEpochSeconds: Long = 1_735_689_600L // 2025-01-01T00:00:00Z
) {
    // 23 бита на timestamp
    private val timestampBits = 23
    private val sequenceBits = 8

    private val maxSequence = (1 shl sequenceBits) - 1   // 255
    private val maxTimestamp = (1 shl timestampBits) - 1L // 8_388_607

    private var lastTimestamp: Long = -1
    private var sequence: Int = 0

    @Synchronized
    fun nextId(): Int {
        var timestamp = Instant.now().epochSecond - customEpochSeconds

        if (timestamp < 0) {
            throw IllegalStateException("Текущее время раньше custom epoch")
        }
        if (timestamp > maxTimestamp) {
            throw IllegalStateException(
                "Timestamp переполнился: $timestamp > $maxTimestamp. " +
                "Установите новый custom epoch."
            )
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) and maxSequence
            if (sequence == 0) {
                // sequence переполнён — ждём следующую секунду
                while (timestamp <= lastTimestamp) {
                    Thread.sleep(1)
                    timestamp = Instant.now().epochSecond - customEpochSeconds
                }
            }
        } else {
            sequence = 0
        }

        lastTimestamp = timestamp

        // Склеиваем: timestamp в старших битах, sequence в младших
        return ((timestamp.toInt() shl sequenceBits) or sequence)
    }
}
```

Пример использования:

```kotlin
fun main() {
    val generator = Int32TimeSortedIdGenerator()

    // Генерация 5 ID
    repeat(5) {
        val id = generator.nextId()
        println("ID: $id")
    }

    // ID отсортированы по времени: каждый следующий >= предыдущему
}
```

## Версия с node ID (для нескольких инстансов)

Если нужна распределённая генерация, придётся урезать sequence:

```kotlin
/**
 * 31-битный time-sorted ID с поддержкой нескольких узлов.
 *
 * Структура:
 *   [31..11] — 21 бит timestamp (секунды) → ~24 дня от epoch
 *   [10..7]  — 4 бита node ID (до 16 узлов)
 *   [6..0]   — 7 бит sequence (128 ID/сек на узел)
 */
class Int32DistributedIdGenerator(
    private val nodeId: Int,
    private val customEpochSeconds: Long = 1_735_689_600L
) {
    init {
        require(nodeId in 0..15) { "nodeId должен быть 0..15" }
    }

    private val timestampBits = 21
    private val nodeBits = 4
    private val sequenceBits = 7

    private val maxSequence = (1 shl sequenceBits) - 1
    private val maxTimestamp = (1L shl timestampBits) - 1

    private val sequenceShift = 0
    private val nodeShift = sequenceBits
    private val timestampShift = sequenceBits + nodeBits

    private var lastTimestamp: Long = -1
    private var sequence: Int = 0

    @Synchronized
    fun nextId(): Int {
        var timestamp = Instant.now().epochSecond - customEpochSeconds
        require(timestamp in 0..maxTimestamp) {
            "Timestamp вне диапазона: $timestamp. Обновите custom epoch."
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) and maxSequence
            if (sequence == 0) {
                while (timestamp <= lastTimestamp) {
                    Thread.sleep(1)
                    timestamp = Instant.now().epochSecond - customEpochSeconds
                }
            }
        } else {
            sequence = 0
        }

        lastTimestamp = timestamp

        return (timestamp.toInt() shl timestampShift) or
               (nodeId shl nodeShift) or
               (sequence shl sequenceShift)
    }
}
```

## Сводка компромиссов

| Свойство | Одиночный узел | Распределённый (4 бита node) |
|----------|---------------|---------------------------|
| Timestamp | 23 бита, ~97 дней | 21 бит, ~24 дня |
| Throughput | 256 ID/сек | 128 ID/сек на узел |
| Узлов | 1 | до 16 |
| Сортировка | по времени | по времени, потом по node, потом по sequence |

## Когда int32 НЕ подходит

- Нужен срок службы больше года → берите `long` (64 бита) и Snowflake/TSID
- Высокий throughput (>1000 ID/сек) → TSID или Snowflake
- Несколько десятков узлов → Snowflake (10 бит node)
- Глобальная уникальность без координации → UUID v7 [```2```](https://stackoverflow.com/questions/18244897/how-to-generate-time-based-uuids)

`int32` с time-based сортировкой — это нишевый инструмент для короткоживущих систем или систем с известным потолком нагрузки. Если контролируете эти ограничения — кастомный генератор выше работает надёжно. Если нет — стоит пересмотреть требование к размеру ID в сторону `long`.

Если один узел и размер sequence можно ужать, главное — правильно распределить оставшиеся биты под timestamp. Чем меньше sequence, тем больше срок жизни ID — но тем ниже потолок генерации в секунду.

## Оптимальные варианты (31 бит, знаковый `int`)

| Sequence бит | IDs/сек | Timestamp бит | Срок жизни от epoch |
|:---:|:---:|:---:|:---:|
| 4 | 16 | 27 | ~4.2 года |
| 2 | 4 | 29 | ~17 лет |
| 1 | 2 | 30 | ~34 года |
| 0 | 1 | 31 | ~68 лет (риск коллизий!) |

С 0 бит sequence Collisions гарантированы, если два ID генерируются в одну секунду. Минимальный разумный минимум — **2 бита (4 IDs/сек)**, что даёт ~17 лет жизни.

## Реализация с настраиваемым размером sequence

```kotlin
import java.time.Instant

class Int32TimeSortedGenerator(
    private val sequenceBits: Int = 4,
    private val customEpochSeconds: Long = 1_735_689_600L // 2025-01-01T00:00:00Z
) {
    init {
        require(sequenceBits in 0..8) { "sequenceBits должен быть 0..8" }
    }

    private val timestampBits = 31 - sequenceBits
    private val maxSequence = (1 shl sequenceBits) - 1
    private val maxTimestamp = (1L shl timestampBits) - 1

    private var lastTimestamp: Long = -1
    private var sequence: Int = 0

    @Synchronized
    fun nextId(): Int {
        var ts = Instant.now().epochSecond - customEpochSeconds

        require(ts >= 0) { "Текущее время раньше custom epoch" }
        require(ts <= maxTimestamp) {
            "Timestamp переполнен: $ts > $maxTimestamp. Установите новый epoch."
        }

        if (sequenceBits == 0) {
            // Без sequence: возвращаем чистый timestamp.
            // Коллизия при повторном вызове в ту же секунду!
            return ts.toInt()
        }

        if (ts == lastTimestamp) {
            sequence = (sequence + 1) and maxSequence
            if (sequence == 0) {
                // Sequence переполнен — ждём следующую секунду
                while (ts <= lastTimestamp) {
                    Thread.sleep(1)
                    ts = Instant.now().epochSecond - customEpochSeconds
                }
            }
        } else {
            sequence = 0
        }

        lastTimestamp = ts
        return (ts.toInt() shl sequenceBits) or sequence
    }
}
```

## Рекомендуемая конфигурация

**2 бита sequence, 29 бит timestamp:**

```kotlin
val generator = Int32TimeSortedGenerator(sequenceBits = 2)
// 4 IDs/сек, срок жизни ~17 лет от epoch
```

Этого хватит для большинства low-throughput систем: API с невысоким трафиком, фоновые джобы, логирование событий. 4 ID в секунду — не так уж мало для системы, где ID генерируются спорадически.

Если нужен запас — **4 бита (16 IDs/сек, ~4 года)**. Четыре года для большинства проектов — это полный жизненный цикл, а epoch легко сдвинуть.

## Если используете unsigned INT в БД

Добавьте один бит — `timestampBits` становится 32 − `sequenceBits`:

```kotlin
class UInt32TimeSortedGenerator(
    private val sequenceBits: Int = 2,
    private val customEpochSeconds: Long = 1_735_689_600L
) {
    private val timestampBits = 32 - sequenceBits
    private val maxSequence = (1 shl sequenceBits) - 1
    private val maxTimestamp = (1L shl timestampBits) - 1

    private var lastTimestamp: Long = -1
    private var sequence: Int = 0

    @Synchronized
    fun nextId(): Int {
        var ts = Instant.now().epochSecond - customEpochSeconds
        require(ts in 0..maxTimestamp) { "Timestamp вне диапазона" }

        if (sequenceBits == 0) return ts.toInt()

        if (ts == lastTimestamp) {
            sequence = (sequence + 1) and maxSequence
            if (sequence == 0) {
                while (ts <= lastTimestamp) {
                    Thread.sleep(1)
                    ts = Instant.now().epochSecond - customEpochSeconds
                }
            }
        } else {
            sequence = 0
        }
        lastTimestamp = ts
        return (ts.toInt() shl sequenceBits) or sequence
    }
}
```

С unsigned и 2 битами sequence получаем **~34 года** жизни и 4 ID/сек. С 4 битами — **~8.5 лет** и 16 ID/сек. Это уже вполне долговечное решение.

