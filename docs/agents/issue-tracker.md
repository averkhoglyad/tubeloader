# Issue tracker: локальный markdown

Задачи живут markdown-файлами в этом репозитории, в `etc/issues/`. Спека — отдельно, в `docs/`.

## Конвенции

- Фича — директория: `etc/issues/<feature>/`
- Тикет — один файл: `etc/issues/<feature>/NN-<slug>.md`, нумерация с `01` в порядке зависимостей,
  блокеры первыми. Никогда не один общий файл на все тикеты.
- Человеческий индекс — `etc/issues/INDEX.md`. Обязателен к обновлению при добавлении, изменении
  и смене статуса фичи или тикета. Агент читает статусы из шапок тикетов, индекс ему не нужен.
- Спека — `docs/<feature>/spec.md`. См. [specs.md](specs.md).

## Шапка тикета

```
# NN: <Title>

**Spec:** docs/<feature>/spec.md
**Blocked by:** NN, NN | None (can start immediately)
**Status:** ready-for-agent
```

Комментарии дописываются внизу файла под заголовком `## Comments`.

## Статусы

`ready-for-agent` ставит `/to-tickets`. `claimed` и `resolved` ставит `/wayfinder`. Остальные —
пять триаж-ролей из [triage-labels.md](triage-labels.md).

## Когда навык говорит «опубликовать в трекер»

Создать файл в `etc/issues/<feature>/`, создав директорию при необходимости, и добавить строку
в `etc/issues/INDEX.md`.

## Когда навык говорит «получить тикет»

Прочитать файл по указанному пути. Пользователь обычно передаёт путь или номер напрямую.

## Проверка дрейфа индекса

```powershell
Get-ChildItem etc/issues -Recurse -Filter *.md |
  Select-String -Pattern '^\*\*Status:\*\*' | ForEach-Object { '{0} -> {1}' -f $_.Filename, $_.Line }
```

## Wayfinding

Используется `/wayfinder`. **Map** — один файл, **child** — файл на тикет.

- **Map**: `etc/issues/<effort>/map.md`, тело — Notes / Decisions-so-far / Fog.
- **Child ticket**: `etc/issues/<effort>/issues/NN-<slug>.md`. Строка `Type:` хранит тип
  (`research` / `prototype` / `grilling` / `task`), строка `Status:` — `claimed` / `resolved`.
- **Blocking**: строка `Blocked by: NN, NN` вверху. Тикет разблокирован, когда все перечисленные
  файлы `resolved`.
- **Frontier**: открытые, незаблокированные и незанятые файлы в `etc/issues/<effort>/issues/`;
  первый по номеру выигрывает.
- **Claim**: поставить `Status: claimed` до начала работы.
- **Resolve**: дописать ответ под `## Answer`, поставить `Status: resolved`, затем добавить
  указатель на контекст в Decisions-so-far в `map.md`.
