# WEEEK как трекер для флоу планирования: проверка public API

**Версия:** 1.0
**Статус:** draft / for review
**Область:** оценить WEEEK public API как замену локальному markdown-трекеру (`etc/issues/`)

---

## 1. Назначение и метод

Вопрос: годится ли WEEEK как хранилище тикетов для флоу `/wayfinder` → `/grill-with-docs` →
`/to-spec` → `/to-tickets` → `/implement`, настроенного в `docs/agents/issue-tracker.md`.

Метод: не чтение документации, а запросы против живого workspace. База
`https://api.weeek.net/public/v1`, заголовок `Authorization: Bearer <token>`. Токен берётся из
переменной окружения, на диск не пишется. Ниже каждая claim сопровождается наблюдённым ответом;
всё непроверенное вынесено в §8.

---

## 2. Источникам вокруг WEEEK-API верить нельзя

Оба публичных проекта, которые выглядят как источник истины, описывают поверхность неверно.

- `weeek-mcp` (README): «Weeek's public API has no comments at all» — **опровергнуто**:
  `GET /tm/tasks/{id}/comments` отвечает 200.
- `weeek-cli`: вендоренный спек содержит 153 операции и ни одной comment-команды; в его
  `docs/COMMANDS.md` нет `tasks/{taskId}/comments`. Snapshot старше появления комментариев,
  для комментариев CLI непригоден.

Официальной машинночитаемой спеки нет. `developers.weeek.net` — Zudoku SPA: `/openapi.json`,
`/sitemap.xml` и любой `/api/*` отдают один и тот же 60-словный shell. Спек лежит в ES-модуле
сборки документации:

```
GET /                                -> /assets/entry.client-<hash>.js
GET /assets/entry.client-<hash>.js   -> lazy-чанки, среди них weeek.yaml-<hash>.js
GET /assets/weeek.yaml-<hash>.js     -> ES-модуль, экспорты schema и slugs
```

Живой спек (`schema`): OpenAPI 3.1.1, **99 paths / 157 operations**, 35 схем, сервер
`https://api.weeek.net/public/v1`, один securityScheme `http/bearer`. Ни OAuth, ни scopes, ни
webhooks. Хеши меняются на каждом деплое доков, поэтому ссылка нестабильна по построению.

---

## 3. Ресурсы и их пригодность

| Наша сущность | WEEEK | Пригодность |
|---|---|---|
| Фича | `project` | да |
| Тикет | `task` | да |
| 5 триаж-ролей + `claimed`/`resolved` | `board-column` (канбан) | да |
| `bug` / `enhancement` / `wayfinder:*` | `tag` | да |
| Claim тикета | assignee | да |
| `**Spec:** docs/<feature>/spec.md` | custom field | да |
| Map → child tickets | `parentId` | да |
| `Blocked by` | нет нативного аналога | обход, §6 |
| Комментарии (`Triage Notes`, brief, `## Answer`) | `POST /tm/tasks/{id}/comments` | да, §4 |
| Тело тикета | `description` | только при создании, §5 |

Конверты ответов не унифицированы: `{success,user}`, `{success,workspace}`, `{success,projects}`,
`{success,tasks,hasMore}`, `{success,tags}`, `{success,data}`, `{success,comment}`. Полезная
нагрузка — единственный ключ, не входящий в `success|sucess|hasMore|hasMoreDeals`.

---

## 4. Комментарии: есть, markdown, append-only

Главная находка, разворачивающая первичную оценку. В public API три операции:

| Операция | Ответ |
|---|---|
| `GET /tm/tasks/{taskId}/comments` (`limit`, `offset`) | `{success, comments[], hasMore}` |
| `POST /tm/tasks/{taskId}/comments` — тело `{markdown, parentId}` | `{success, comment}` |
| `DELETE /tm/tasks/{taskId}/comments/{commentId}` | **204**, удаление настоящее |

Схема `Comment`: `id`, `parentId`, `authorId` (uuid), `markdown` («Comment content in Markdown
format»), `createdAt`, `updatedAt`.

Наблюдённые ответы:

```
200 POST /tm/tasks/{id}/comments   markdown echo equal=true      <- сохранён байт-в-байт
200 GET  /tm/tasks/{id}/comments   len=1 hasMore=false
422 POST /tm/tasks/{id}/comments   errors={"markdown":["The field is required."]}   <- пустой markdown
204 DELETE /tm/tasks/{id}/comments/{commentId}
200 GET  /tm/tasks/{id}/comments   len=0                          <- комментарий исчез
```

Правки комментария нет ни в каком виде: `PUT` и `POST` на `/comments/{commentId}` отвечают
`405 The PUT method is not supported for route public/v1/tm/tasks/9/comments/1. Supported methods: DELETE.`

Практический смысл: все дописывания по ходу работы — triage-заметки, agent brief, resolution
comment, `## Answer` — ложатся в markdown-комментарий без потерь форматирования. Append-only
модель совпадает с тем, как флоу и так работает.

---

## 5. Тело задачи: `description` заморожен, `customFields` обновляется

`description` принимается только при создании, и то как HTML — отправленный markdown вернулся
переупакованным:

```
POST /tm/tasks   description="PROBE description v1\n\n- bullet one\n- bullet two"
GET  /tm/tasks/N description="<p>PROBE description v1- bullet one\n- bullet two</p>"   len=52
```

`PUT /tm/tasks/{id}` игнорирует поле молча. Проверено с контролем в том же вызове: `title`
изменился, `description` — нет; отдельный `PUT` только с `description` тоже без эффекта. В схеме
тела `PUT` поля `description` нет вовсе.

Вывод: описание тикета фиксируется в момент создания. Всё, что дописывается потом, идёт в
комментарий (§4). Для append-only флоу это не потеря.

Кастомные поля, в отличие от `description`, пишутся и обновляются:

```
300 POST /tm/custom-fields {name, type:"text"}
    -> id=a2d87eb0-… type=text
200 POST /tm/tasks  customFields:{<fieldId>:"9,12"}
    -> echo [{"id":…,"name":"PROBE Blocked by","type":"text","value":"9,12"}]
    GET value="9,12"
200 PUT  /tm/tasks/{id}  customFields:{<fieldId>:"14,15"}     <- обновление работает
    GET value="14,15"
200 PUT  /tm/tasks/{id}  customFields:{<fieldId>:"11, 12"}
    GET value="11, 12"   identical=true                        <- строка не нормализуется
200 PUT  /tm/tasks/{id}  customFields:{<fieldId>:""}
    GET value=null                                             <- снятие значения
200 DELETE /tm/custom-fields/{fieldId}
```

Значение читается из самой задачи: `GET /tm/tasks/{id}` возвращает `customFields` массивом
`{id, name, type, options, config, value}` — один запрос даёт и значения, и метаданные полей.

Важная деталь: запись работает **и до привязки поля к проекту**. Созданное глобально поле
приняло значение на задаче, хотя в проект перенесено не было. Но `weeek-mcp` сообщает
противоположное поведение («Weeek stores nothing when you write to one it doesn't cover, so the
write is verified and reported»). Наблюдение — за то, что запись до привязки работает; расхождение
не объяснено, см. §8.

Типы полей: `text`, `boolean`, `datetime`, `select`, `multiselect`, `member`, `contact`, `link`,
`approval`, `number`. `config` — только для `number` (`number|currency|percent`, `precision` 0..6)
и `boolean` (`radio|checkbox`). Операции доступны в трёх scope: global `/tm/custom-fields`,
project `/tm/projects/{id}/custom-fields`, board `/tm/boards/{id}/custom-fields`; есть перенос
между scope (`transfer-to-project`, `transfer-to-board`, `transfer-to-task-manager`) и управление
порядком опций (`options/move` с `after`/`before`).

---

## 6. `Blocked by`: нативного аналога нет

В UI задачи есть «связь методом»; подкапотом UI ходит на internal-роут
`/ws/{wsid}/tm/tasks/dependencies`. В public API аналога нет, и он живёт на другой базе:

```
root OPTIONS /ws/1218118/tm/tasks/dependencies   200   ALLOW=GET,HEAD,POST,DELETE
root GET     /ws/1218118/tm/tasks/dependencies   401   Unauthenticated.
root POST    /ws/1218118/tm/tasks/dependencies   401   Unauthenticated.
root GET     /ws/1218118/kb/documents            404   The route … could not be found.
```

Роут существует, но Bearer-токен им не принимается — нужна сессия (cookies), то есть путь вроде
`weeek-mcp`. `GET /ws/{wsid}/kb/documents` → 404 показывает, что internal-неймспейс не «отвечает
401 на всё подряд», поэтому 401 на dependencies осмыслен как «роут есть».

**Ловушка, стоившая отдельной проверки.** На public-базе `POST /tm/tasks/dependencies` отвечает
`405 Supported methods: GET, HEAD, PUT, DELETE` — набор методов совпадает с настоящим роутом и
выглядит как доказательство его существования. Это обычный `/tm/tasks/{id}` с id=`dependencies`:

```
pub GET   /tm/tasks/dependencies   400   Model not found
pub GET   /tm/tasks/releasenotes   400   Model not found     <- контроль
pub GET   /tm/tasks/bogusid123     400   Model not found     <- контроль
pub PATCH /tm/tasks/dependencies   405   ALLOW=GET,HEAD,PUT,DELETE   <- как у /tm/tasks/9
pub PATCH /tm/tasks/9              405   ALLOW=GET,HEAD,PUT,DELETE
```

Остальные варианты — чистые 404: `/tm/tasks/{id}/dependencies`, `/tm/dependencies`,
`/tm/relations`, `/tm/tasks/{id}/related`, `/tm/tasks/{id}/links`. В живом спеке (99 paths /
157 operations) dependency-операций нет; в тексте спека `blockedBy`, `blocks`, `dependsOn`,
`dependencies`, `blocker` — 0 вхождений. Пользователь обратился в поддержку WEEEK.

**Что есть вместо:** иерархия, и она переписываемая.

```
POST /tm/tasks {parentId: N}                -> parentId-echo=N
POST /tm/tasks/{childId}/parent {parentId:N}  200   <- дозапись после создания
GET  /tm/tasks/{N}  subTasks=[childA, childB]       <- реальные дети, не декорация
POST /tm/tasks/{childId}/parent {parentId:null}  200  <- отцепка
```

`parentId` принимается и при создании, и постфактум. Для дерева этого достаточно; для DAG
(несколько блокеров на тикет) — нет.

**Обход через кастомное поле.** `text` со списком id блокеров: пишется при создании, обновляется
через `PUT`, читается из задачи (§5). Ограничения: нет валидации и индексации, цикл A→B→A не
ловится, frontier требует прочитать все задачи и распарсить строку. Против этого варианта играет
то, что `parentId` уже даёт иерархию и `subTasks`, а `select`/`multiselect` для связи
задача→задача не годятся: они хранят id **опции**, а не задачи.

---

## 7. Статусы и прочие наблюдения

- Статусная модель — колонки доски. `GET /tm/boards?projectId={id}` → `boards[]`
  (`id`, `name`, `projectId`, `isPrivate`); `GET /tm/board-columns?boardId={id}` →
  `boardColumns[]` (`id`, `name`, `boardId`). Без `projectId` и `boardId` соответственно — 422.
- Удаление задач **мягкое**: `DELETE` → 200, `GET` по id → 200 с `isDeleted: true`, обычный
  листинг запись исключает, `?all=1` — включает. Hard delete в public API нет, запись остаётся в
  корзине. Комментарии удаляются жёстко — асимметрия.
- Ошибки: неизвестный роут → 404 `The route public/v1/… could not be found.`; неизвестный id →
  **400** `Model not found`; обе формы `{success:false, message}`.
- Booleans на проводе только `0`/`1`: `?completed=true` → 422 `The completed field must be true or
  false.`, `?completed=1` → 200.
- `GET /tm/tasks` принимает `day`, `userId`, `projectId`, `completed`, `boardId`, `boardColumnId`,
  `type`, `priority`, `tags`, `search`, `perPage`, `offset`, `sortBy`, `startDate`, `endDate`,
  `completedAtFrom`, `completedAtTo`, `all`. Пагинация только здесь; в ответе `hasMore`.
- `POST /tm/tasks`: `title`, `description`, `day`, `parentId`, `userId`, `locations` (required),
  `type` (`action|meet|call`), `priority` (`0|1|2|3`), `customFields`. `PUT /tm/tasks/{id}`:
  `title`, `priority`, `type`, `startDate`, `dueDate`, `startDateTime`, `dueDateTime`, `duration`,
  `tags`, `customFields`.
- `GET /tm/tasks/{id}` отдаёт 43 поля, включая `boardId`, `boardColumnId`, `projectId`, `subTasks`,
  `tags`, `assignees`, `customFields`, `isDeleted`.

---

## 8. Не проверено

- **429 и `Retry-After`** — не провоцировал. `weeek-cli` тоже отмечает их как непроверенные.
- **`weeek-mcp` против наблюдения в §5** — README утверждает, что запись в поле, не покрывающее
  задачу, молча теряется. В пробе значение сохранилось у поля, ещё не привязанного к проекту.
  Расхождение не исследовано: не исключено, что «не покрывает» относится к полю, созданному для
  доски, а не глобально.
- **`isPrivate` и перенос задач между проектами** — не трогал.
- **Связь «связь методом» из UI** — какой именно формат тела у internal-роута, не выяснено:
  без сессии его не вызвать.

---

## 9. Вывод

WEEEK покрывает флоу почти целиком: задачи-тикеты, колонки как статусы, теги, assignee, иерархия
через `parentId`, спецификация через кастомное поле. Комментарии в markdown закрывают запись
brief'ов, triage-заметок и resolution'ов, а ограничение `description` (только при создании) для
append-only флоу безразлично.

Единственный настоящий разрыв — отсутствие нативного `Blocked by` в public API. Он обходится
кастомным `text`-полем или `parentId`, но в обоих случаях frontier придётся вычислять запросами,
а не видеть в интерфейсе, как того хочет `/wayfinder`. Решение о переходе откладывается до
ответа поддержки WEEEK.

Практическое следствие для реализации: не искать dependency-эндпоинт на public-базе и не
принимать `405` за доказательство существования роута — на `/tm/tasks/{id}` он выдаёт тот же
набор методов.
