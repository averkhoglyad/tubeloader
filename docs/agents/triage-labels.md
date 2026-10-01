# Triage labels

Навыки говорят о триаж-ролях и о типах wayfinder-тикетов. Здесь — строки меток, которыми эти роли
выражены в GitHub-репозитории `averkhogliad/tube-loader`, и их назначение.

Метки уже заведены в репозитории. Скрипт `scripts/tracker/tracker.mjs` этого словаря не знает —
`--label` уходит в GitHub как есть, поэтому строки брать отсюда.

## Триаж-роли

При триаже issue несёт ровно одну роль категории и ровно одну роль статуса.

| Роль в mattpocock/skills | Метка | Цвет | Значение |
| --- | --- | --- | --- |
| `bug` | `bug` | `#d73a4a` | Что-то сломано |
| `enhancement` | `enhancement` | `#a2eeef` | Новая возможность или улучшение |
| `needs-triage` | `needs-triage` | `#fbca04` | Мейнтейнер должен оценить |
| `needs-info` | `needs-info` | `#d4c5f9` | Ждём информации от репортёра |
| `ready-for-agent` | `ready-for-agent` | `#0e8a16` | Полностью описано, готово для агента |
| `ready-for-human` | `ready-for-human` | `#1d76db` | Нужна реализация человеком |
| `wontfix` | `wontfix` | `#ffffff` | Не будет сделано |

Категорию ставят при триаже; `bug` и `enhancement` заведены с описаниями и уже существуют.

## Типы wayfinder-тикетов

| Тип | Метка | Цвет |
| --- | --- | --- |
| карта | `wayfinder:map` | `#5319e7` |
| исследование | `wayfinder:research` | `#c2e0c6` |
| прототип | `wayfinder:prototype` | `#bfdadc` |
| гриллинг | `wayfinder:grilling` | `#d4c5f9` |
| задача | `wayfinder:task` | `#fef2c0` |

Дети карты дополнительно связываются с ней нативной связью sub-issue.

## Дефолтные метки GitHub

`documentation`, `duplicate`, `good first issue`, `help wanted`, `invalid`, `question` существуют в
репозитории со времён создания и в этом флоу не используются.
