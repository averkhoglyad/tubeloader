# Kotlin/JVM + Compose Desktop для ядра и GUI

Ядро Tubeloader — Kotlin/JVM, GUI — Compose Desktop. Выбран единый язык для скорости
достижения M1: существующий Rutube-прототип на Kotlin переносится без переписывания на
другой язык, Compose Desktop даёт stable API без IPC-границы между ядром и фронтендом.

## Considered Options

- **Kotlin/JVM + Compose Desktop** (принято): единый язык, перенос прототипа, stable API,
  jpackage для дистрибуции. Win64-first.
- **Kotlin/JVM + Rust+gpui**: polyglot, IPC между JVM и Rust-процессом. gpui — pre-1.0,
  нет semver, docs sparse, экосистема маленькая, Windows получает core fixes в 2026.
  См. `docs/research/gpui-assessment.md`. Отклонён как high-risk.
- **Rust для ядра**: лучший дистрибутив (нативный бинарник), но full rewrite прототипа.
  Отклонён: скорость M1 важнее долгосрочных свойств дистрибуции на данном этапе.

## Consequences

- Дистрибуция через jpackage (JVM bundle), не нативный бинарник.
- Runtime JVM в поставке — больше размер, но приемлемо для десктоп-приложения.
- Команды вниз / события вверх — через Kotlin coroutines channels/SharedFlow, без IPC.
- Тикет 04 (стратегия Rutube) остаётся открытым — решается в `/grill-with-docs`.
