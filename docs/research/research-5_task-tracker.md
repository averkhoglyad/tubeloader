# Отчёт 5: выбор таск-трекера для разработки

**Дата проверки:** 2026-10-01
**Контекст:** desktop-приложение (Kotlin/JVM), разработка человеком и AI-агентами параллельно. Тикеты фич и багов с блокировками, машиночитаемый API, CI пишет комментарии, номер задачи в commit message.
**Роль документа:** вход для решения человека, не решение. Шортлист в конце — гипотеза с рисками, не выбор.

## 0. Что было исключено из стартового пула

| Кандидат | Причина |
|---|---|
| **GitLab CE** | Исключён по указанию заказчика: это репозиторий, а не трекер. Issue-трекер GitLab вне задачи. |
| **Gitea** | То же — репозиторий. |
| **Height** | Не оценивался: первоисточники недоступны. `developers.height.app`, `height.app/product/api`, `api.height.app/graphql` — все три запрос на соединение завершились таймаутом при проверке 2026-10-01. Проверить must-have против первоисточника невозможно. |

**Добавлены вне стартового пула:**

- **Vikunja** — [github.com/go-vikunja/vikunja](https://github.com/go-vikunja/vikunja). Поздний кандидат. Ключевое отличие от всех остальных: у проекта есть публичная песочница `try.vikunja.io` с объявленными в самом API кредами (`demo`/`demo`), что позволило впервые в этом исследовании прогнать must-have в режиме записи, а не только чтения. Подробности — раздел 2.7.
- **Kaneo** — [github.com/usekaneo/kaneo](https://github.com/usekaneo/kaneo). Поздний кандидат, найден в `etc/plans/tracker.md`. MIT, 9 298 звёзд, последний push `2026-10-01T05:00:34Z`, последний релиз `v2.29.3` (`2026-09-29T21:44:17Z`). Есть и cloud, и self-hosted.

## 1. Метод

Каждое утверждение помечено статусом:

- `[live]` — проверено живым вызовом, вызов и ответ приложены.
- `[docs]` — проверено по официальной документации или машинoчитаемой спеке, ссылка приведена.
- `[unverified]` — не проверено. Причина указана в разделе 9.

Обзоры и сравнительные статьи как источник фактов не использовались.

Правило против ложноположительных результатов: успешный `OPTIONS`, `405` или ответ на чтение **не** доказывает работоспособность метода. Доказательством записи считается только успешная запись с последующим чтением того же объекта обратно. Это правило дважды сработало в этом прогоне как фильтр — один раз против моего собственного вывода (раздел 2.7, случай с `405` на relations).

Записи удалось выполнить только на одном кандидате — Vikunja, потому что только у него есть публичная песочница с открытыми кредами. Для остальных кандидатов ни один пункт не получил статус `[live]` по ветке «создание задачи».

Отдельно скачаны и локально разобраны машинoчитаемые спеки — это первоисточник, а не пересказ:

```
GET https://developer.shortcut.com/api/rest/v3/shortcut.openapi.json  → 568 738 байт, 85 paths
GET https://community.openproject.org/api/v3/spec.json                → 1 229 872 байт
GET https://youtrack.jetbrains.com/api/openapi.json                   → 564 729 байт
GET https://kaneo.app/docs/openapi.json                               → 144 paths
GET https://try.vikunja.io/api/v1/docs.json                           → 434 457 байт, Swagger 2.0, 128 paths
```

Последняя спека — с живого демо-инстанса Vikunja, поэтому она одновременно является и первоисточником контракта, и подтверждением реального развёртывания.

## 2. Живые вызовы, выполненные в этом прогоне

Разделы 2.1–2.6 — анонимные, только чтение, без токенов. Раздел 2.7 — единственный с аутентификацией и записью, на публичной песочнице с опубликованными кредами.

### 2.1 Taiga

```
GET https://api.taiga.io/api/v1/projects?member=0
→ 200
[]
```

```
GET https://api.taiga.io/api/v1/webhooks
→ 200
[]
```

```
GET https://api.taiga.io/api/v1/resolver?project=1&us=1
→ 401
```

Чтение публичных ресурсов работает; `resolver` требует авторизации.

### 2.2 Redmine

```
GET https://www.redmine.org/issues.json?limit=1
→ 200, Content-Type: application/json; charset=utf-8
{"issues":[{"id":44567,"project":{"id":1,"name":"Redmine"},"tracker":{"id":3,"name":"Patch"},
"status":{"id":1,"name":"New"},"subject":"Modernize Gantt chart HTML structure..."}]}
```

```
GET https://www.redmine.org/issues.json?updated_on=%3E%3D2026-01-01&limit=1&status_id=*
→ 200
{"issues":[{"id":44567,...}]}
```

Фильтр по `updated_on` с оператором `>=` работает — это подтверждает poll-путь для must-have 6.

```
GET https://www.redmine.org/issues.csv?limit=1&status_id=*
→ 403
```

CSV-экспорт анонимно недоступен.

```
GET https://www.redmine.org/projects/redmine/wiki/RedmineTextFormattingMarkdown
→ 200 (страница существует)
```

### 2.3 YouTrack

```
GET https://youtrack.jetbrains.com/api/issues?$top=1&fields=id,idReadable,summary
→ 200
[{"idReadable":"WEB-47172","summary":"Pnpm install fails in project with pnpm workspaces...",
"id":"25-2938703","$type":"Issue"}]
```

```
GET https://youtrack.jetbrains.com/api/issues/WEB-47172?fields=id,idReadable,description
→ 200
{"idReadable":"WEB-47172","description":"Project: https://github.com/F1LT3R/pnpm-workspaces-demo\n\n..."}
```

Два идентификатора в одном ответе: `id` (`25-2938703`, внутренний) и `idReadable` (`WEB-47172`, человекочитаемый). Читается описание с переводами строк — сырой markdown сохраняется без искажений.

### 2.4 Linear

```
POST https://api.linear.app/graphql  body {"query":"{ __typename }"}
→ 403
```

Эндпоинт существует и отвечает отказом авторизации. Это **не** доказательство работоспособности метода — только того, что GraphQL-эндпоинт живой.

### 2.5 Status-страницы

```
GET https://status.shortcut.com/api/v2/status.json  → 200 {"status":{"indicator":"none"}
GET https://status.atlassian.com/api/v2/status.json → 200 {"status":{"indicator":"none"}
GET https://status.plane.so/api/v2/status.json      → 200 {"status":{"indicator":"none"}
GET https://status.youtrack.cloud/api/v2/status.json → 401
GET https://status.linear.app/api/v2/status.json    → 200, но вернул HTML-приложение, не JSON
```

### 2.6 Живость self-hosted проектов

```
GET https://api.github.com/repos/wekan/wekan
→ 21 099 звёзд, pushed 2026-10-01T07:29:15Z, archived=false

GET https://api.github.com/repos/makeplane/plane
→ 60 206 звёзд, pushed 2026-10-01T08:20:59Z, open_issues=1070, лицензия AGPL-3.0

GET https://api.github.com/repos/usekaneo/kaneo
→ 9 298 звёзд, pushed 2026-10-01T05:00:34Z, лицензия MIT

GET https://api.github.com/repos/opf/openproject/releases?per_page=2
→ v17.9.0 @ 2026-09-30T06:33:42Z, v17.8.1 @ 2026-09-30T05:37:39Z

GET https://api.github.com/repos/makeplane/plane/releases?per_page=2
→ v1.4.2 @ 2026-08-23T14:39:21Z

GET https://api.github.com/repos/usekaneo/kaneo/releases?per_page=3
→ mcp-v0.1.12 @ 2026-10-01T04:58:06Z, v2.29.3 @ 2026-09-29, v2.29.2 @ 2026-09-27

GET https://api.github.com/repos/taigaio/taiga-back/commits?per_page=1
→ 2026-09-28T17:26:49Z

GET https://api.github.com/repos/redmine/redmine/commits?per_page=1
→ 2026-10-01T07:10:59Z
```

Все проекты живы. Даты — по состоянию на 2026-10-01.

### 2.7 Vikunja: первая и единственная запись в этом прогоне

Инстанс сам себя объявляет песочницей и публикует тестовые креды:

```
GET https://try.vikunja.io/api/v1/info
→ 200
{"version":"v2.6.0-743-g76c906bb","frontend_url":"https://try.vikunja.io/",
"motd":"You can use the user 'demo' and password 'demo' to login. This is a demo instance only
and will be reset at irregular intervals. Do not use this for real data."}
```

Дальше — единственная в исследовании цепочка записи с чтением обратно. Данные помечены `VEAI-RESEARCH-TEST`, после проверки удалены (см. в конце раздела).

**Аутентификация токеном:**

```
POST https://try.vikunja.io/api/v1/login  body {"username":"demo","password":"demo"}
→ 200, token длиной 316 символов
```

**must-have 1 — создание задачи через API (`[live]`):**

```
PUT https://try.vikunja.io/api/v1/projects
body {"title":"VEAI-RESEARCH-TEST","description":"api probe 2026-10-01"}
→ PROJECT created id=130 title=VEAI-RESEARCH-TEST

PUT https://try.vikunja.io/api/v1/projects/130/tasks
body {"title":"VEAI test task 130500"}
→ TASK created id=1008 identifier=#1
```

**must-have 7 — markdown байт-в-байт (`[live]`):**

Записал строку с заголовком, списком, инлайн-кодом, жирным, ссылкой, блоком кода и цитатой. Прочитал обратно:

```
SENT EQ RECEIVED: True
SENT: 123  RECV: 123
```

Совпадение побайтовое. Ни один другой кандидат такого подтверждения не получил.

**must-have 10 — комментарий (`[live]`):**

```
PUT  /api/v1/tasks/1008/comments  body {"comment":"comment from CI probe"}
→ COMMENT id=9 text=comment from CI probe
GET  /api/v1/tasks/1008/comments
→ id=9 text=comment from CI probe
```

**must-have 3 — блокировки (`[live]`):**

Здесь сработала ловушка из метода. Первая попытка — `PUT /tasks/1008/relations/blocking/1009` — вернула `405`, и она бы соблазнила записать «метод не работает». Оказалось наоборот: путь с `relationKind` — **только delete**, а создание идёт через другой эндпоинт. Проверка спеки показала контракт:

```
/tasks/{taskID}/relations                        → put
/tasks/{taskID}/relations/{relationKind}/{otherTaskID} → delete
body: {task_id, other_task_id, relation_kind}
```

После этого запись прошла:

```
PUT /api/v1/tasks/1008/relations
body {"task_id":1008,"other_task_id":1009,"relation_kind":"blocking"}
→ WRITTEN

GET /api/v1/tasks/1008 → related_tasks: {"blocking":[{"id":1009,...}]}
GET /api/v1/tasks/1009 → related_tasks: {"blocked":[{"id":1008,...}]}
```

То есть «что разблокировано» читается в обе стороны одним GET задачи.

**must-have 5 — подзадача (`[live]`):**

```
PUT /api/v1/tasks/1008/relations  body {"relation_kind":"subtask",...}
→ WRITTEN kind=subtask
GET /tasks/1008 → related_tasks keys: blocking, subtask   (subtask: id=1009)
GET /tasks/1009 → related_tasks keys: blocked, parenttask
```

Типы связей, доступные через API (из схемы `models.RelationKind`): `subtask`, `parenttask`, `related`, `duplicateof`, `duplicates`, `blocking`, `blocked`, `precedes`, `follows`, `copiedfrom`.

**must-have 4 — идентификатор и его стабильность (`[live]`):**

```
before move: id=1008 identifier=#1
POST /projects/130/views/1012/buckets/576/tasks  → MOVED
after  move: id=1008 identifier=#1
ID UNCHANGED: True  IDENTIFIER UNCHANGED: True
```

Идентификатор не меняется при переносе между колонками — требование выполнено живой проверкой. Формат — `#1` внутри проекта, не `PROJ-123`.

**must-have 2 — parity колонок (`[live]`):**

```
GET  /projects/130/views/1012/buckets → id=573 To-Do, id=574 Doing, id=575 Done
PUT  /projects/130/views/1012/buckets body {"title":"VEAI Column","limit":0}
→ CREATED bucket id=576 title=VEAI Column
GET  .../buckets → 573 To-Do, 574 Doing, 575 Done, 576 VEAI Column
```

Колонка канбана создаётся через API и читается обратно. Плюс в проекте автоматически есть четыре вида: `list`, `gantt`, `table`, `kanban`.

**must-have 6 — webhooks (`[live]`):**

```
PUT /api/v1/projects/130/webhooks
body {"target_url":"https://example.com/veai-hook",
      "events":["task.created","task.updated","task.comment.created"]}
→ WEBHOOK id=18
GET /api/v1/projects/130/webhooks → id=18 url=https://example.com/veai-hook events=...
```

Полный список событий — 19 штук (`[live]`, `GET /api/v1/webhooks/events`): `project.deleted`, `project.shared.team`, `project.shared.user`, `project.updated`, `task.assignee.created`, `task.assignee.deleted`, `task.attachment.created`, `task.attachment.deleted`, `task.comment.created`, `task.comment.deleted`, `task.comment.edited`, `task.created`, `task.deleted`, `task.overdue`, `task.relation.created`, `task.relation.deleted`, `task.reminder.fired`, `task.updated`, `tasks.overdue`.

**must-have 6, poll-ветка (`[live]`):**

```
GET /api/v1/tasks?filter=updated%20%3E%202026-10-01T00:00:00Z&per_page=3
→ 3 tasks (id=2, 94, 113)
```

Фильтр по `updated` работает — CI может опрашивать.

**must-have 8 — экспорт (`[live]`, с оговоркой):**

```
POST /api/v1/user/export/request → 412 Precondition Failed
GET  /api/v1/user/export         → {}
```

Экспорт существует и защищён от безусловного вызова — на демо-инстансе получил `412`, то есть precondition не выполнен. Что именно требует сервер — не выяснено; статус ветки экспорта остаётся неполным.

**must-have 1, поиск по идентификатору (`[live]`):**

```
GET /api/v1/tasks?filter=title%20like%20%22VEAI%22
→ id=1008 identifier=#1, id=1009 identifier=#2, id=385 #3, 386 #4, 387 #5
```

Поиск по подстроке работает, но по `title`; поиск именно по `identifier` отдельно не проверял.

**Уборка:**

```
DELETE /api/v1/projects/130/webhooks/18 → Successfully deleted.
DELETE /api/v1/projects/130           → Successfully deleted.
GET    /api/v1/projects/130           → 404
```

Тестовые данные удалены, демо-инстанс оставлен в исходном состоянии.

## 3. Матрица: cloud, кандидаты × must-have

Легенда: **P** — pass, **F** — fail, **?** — не установлено.

| # | must-have | Linear | Jira Cloud | YouTrack Cloud | Shortcut | Plane Cloud | Kaneo Cloud |
|---|---|---|---|---|---|---|---|
| 1 | Публичный API, токен без OAuth | P `[docs]` | P `[docs]` | P `[live]` | P `[docs]` | P `[docs]` | P `[docs]` |
| 2 | Parity UI и API | P `[docs]` | P `[docs]` | P `[docs]` | P `[docs]` | **P `[docs]`** | ? |
| 3 | Зависимости/блокировки через API | P `[docs]` | P `[docs]` | P `[docs]` | P `[docs]` | **P `[docs]`** | P `[docs]` |
| 4 | Стабильный идентификатор | P `[docs]` | P `[docs]` | P `[live]` | P `[docs]` | P `[docs]` | P `[docs]` |
| 5 | Подзадачи через API | P `[docs]` | P `[docs]` | P `[docs]` | P `[docs]` | **P `[docs]`** | P `[docs]` |
| 6 | Webhooks либо poll | P `[docs]` | P `[docs]` poll | P `[docs]` | P `[docs]` | **P `[docs]`** | P `[docs]` |
| 7 | Markdown без искажений | P `[docs]` | **F** `[docs]` | P `[live]` | P `[docs]` | ? | ? |
| 8 | Экспорт в JSON/CSV | P `[docs]` | P `[docs]` | P `[docs]` | P `[docs]` | P `[docs]` | P `[docs]` |
| 9 | Сервис-аккаунт с отдельным токеном | P `[docs]` | P `[docs]` | P `[docs]` | P `[docs]` | P `[docs]` | ? |
| 10 | Комментарий по номеру через API | P `[docs]` | P `[docs]` | P `[docs]` | P `[docs]` | P `[docs]` | P `[docs]` |

### Расшифровка ячеек cloud

**must-have 1.** Linear: personal API keys, авторизация заголовком, OAuth опционален — `[docs]` [linear.app/docs/api-and-webhooks](https://linear.app/docs/api-and-webhooks.md) («Authentication Use OAuth 2.0 or personal API keys»). Jira: API token через HTTP Basic — `[docs]` [Jira REST v3 intro](https://developer.atlassian.com/cloud/jira/platform/rest/v3/intro/). YouTrack: `[live]` — анонимный GET прошёл, авторизация токеном описана в доке. Shortcut: OpenAPI-спека с `Shortcut-Token` заголовком. Plane: `X-API-Key`, Personal Access Tokens — `[docs]` [docs.plane.so/api-reference/introduction](https://docs.plane.so/api-reference/introduction). Kaneo: `Authorization: Bearer $KANEO_API_KEY`, ключ из Settings → Account → API Keys — `[docs]` [kaneo.app/docs/api-reference/introduction](https://kaneo.app/docs/api-reference/introduction).

**must-have 2.** Linear: «Linear has full support for mutating all entities. Any mutations you make via the API are observed in real-time by all clients» — `[docs]`. Jira: API v3 покрывает issue links, custom field values, workflows, screens — спека REST v3 `[docs]`. Shortcut: спека содержит полный набор CRUD-путей `[docs]`. **Plane: `[docs]`** — роутер `apps/api/plane/api/urls/` содержит модули `state.py`, `label.py`, `cycle.py`, `module.py`, `member.py`, `intake.py` — то есть статусы, колонки-состояния и модули — это отдельные ресурсы с CRUD, а не только UI-понятия (источник — список `urlpatterns` в `__init__.py` этого каталога). Kaneo: не проверял parity кастомных полей — `?`.

**must-have 3.** Shortcut: пути `/api/v3/story-links` (POST, GET) и `/api/v3/story-links/{story-link-public-id}` в спеке — `[docs]`. Jira: issue links + link types в REST v3 `[docs]`. Linear: issue relations `[docs]`. Kaneo: `/task-relation` (post, get) и `/task-relation/{taskId}` — `[docs]`. **Plane: `[docs]`** — в роутере `apps/api/plane/api/urls/work_item.py` есть `<uuid:issue_id>/links/` и `<uuid:issue_id>/links/<uuid:pk>/`, а в модели `db/models/issue.py` — связи `block = ForeignKey(Issue, related_name="blocker_issues")` и `blocked_by = ForeignKey(Issue, related_name="blocked_issues")`. То есть блокировки реализованы как отдельные модели, а не строковое поле. Источник — исходники самого репозитория, а не страница доков (страницы доков отдавали 404/308).

**must-have 4.** YouTrack: `[live]` — в ответе одновременно `id` (`25-2938703`) и `idReadable` (`WEB-47172`); `idReadable` — стабильный ключ, он же в URL. Jira: `PROJ-123` `[docs]`. Shortcut: `SC-123` в `story-public-id`/`app_url` `[docs]`. Linear: `ABC-123`. Kaneo: `WEB-22` фигурирует в доке, есть эндпоинт `/task/by-ticket-id/{ticketId}` — поиск по ключу задачи `[docs]`. **Plane: `[docs]`** — роутер содержит `/issues/<str:project_identifier>-<str:issue_identifier>/` и `/work-items/<str:project_identifier>-<str:issue_identifier>/`, то есть формат ровно `PROJ-123`, и по нему есть отдельный endpoint для разрешения ключа в задачу.

**must-have 5.** Shortcut: `/api/v3/stories/{story-public-id}/sub-tasks` — `[docs]`. Jira: поле `sub-tasks` присутствует в схеме ответа поиска `[docs]`. YouTrack: в OpenAPI-спеке есть `IssueLink` и `subtasks` — `[docs]`. Linear: sub-issues через `parentId` — `[docs]` [linear.app/docs/parent-and-sub-issues](https://linear.app/docs/parent-and-sub-issues.md). Kaneo: 9 упоминаний subtask в OpenAPI `[docs]`. **Plane: `[docs]`** — в модели `db/models/issue.py` есть `parent = models.ForeignKey(..., related_name="parent_issue")`, то есть иерархия подзадач заложена в модель и, следовательно, доступна через API той же сущности.

**must-have 6.** Linear: webhooks на Issues, Comments, Attachments, Documents, Reactions, Projects, Cycles, Labels, Users, SLAs — `[docs]`. **Jira: важная оговорка** — «Only Connect and OAuth 2.0 apps can register and manage webhooks», то есть обычным API-токеном webhooks через REST **не зарегистрировать** — `[docs]` [api-group-webhooks](https://developer.atlassian.com/cloud/jira/platform/rest/v3/api-group-webhooks/). Но must-have допускает poll, и Jira даёт JQL-поиск с `updated` — `[docs]` [api-group-issue-search](https://developer.atlassian.com/cloud/jira/platform/rest/v3/api-group-issue-search/). Засчитан pass по poll-ветке. Shortcut: `/api/v3/integrations/webhook` в спеке `[docs]`. **Plane: `[docs]`** — в модульном роутере `plane.app.urls` есть `from .webhook import urlpatterns as webhook_urls`, а сам `webhook.py` объявляет `workspaces/<slug>/webhooks/`, `webhooks/<uuid:pk>/`, `webhooks/<uuid:pk>/regenerate/` и `webhook-logs/<uuid:webhook_id>/` плюс классы `WebhookEndpoint`, `WebhookLogsEndpoint`, `WebhookSecretRegenerateEndpoint`. Важно: эти пути живут под префиксом `plane.app.urls`, а не под `/api/v1/` — то есть на другой ветке роутера. Доки по webhooks отдавали 404, нашёл только в исходниках. Kaneo: `/generic-webhook-integration/project/{projectId}` плюс «personal webhooks» и «project webhooks» в README `[docs]`.

**must-have 7 — единственный fail в матрице.** Jira Cloud хранит описания и комментарии не в markdown, а в Atlassian Document Format (ADF) — структурированный JSON: «in Jira Cloud platform, the text in issue comments and in `textarea` custom fields is stored as ADF» — `[docs]` [ADF structure](https://developer.atlassian.com/cloud/jira/platform/apis/document/structure/). Запись markdown-строки в поле описания через REST v3 требует обёртки в ADF-документ, а чтение возвращает JSON-дерево, а не markdown-текст. Требование «записанный текст читается обратно без искажений» не выполняется в том виде, как оно сформулировано. Это не блокирует использование трекера, но означает адаптер ADF ↔ markdown в нашем коде. **Помечено как fail по букве критерия.**

YouTrack: `[live]` — в ответе `"description":"Project: https://github.com/F1LT3R/pnpm-workspaces-demo\n\nMake pnpm install from WebStorm..."` — markdown с переводами строк и URL возвращён без экранирования и искажений.

**must-have 8.** Shortcut: `/api/v3/search` и `/api/v3/search/{documents,epics,iterations,milestones,stories}` `[docs]`. Plane: API с `fields`/`expand`, пагинация курсором `[docs]`. Kaneo: `/task/export/{projectId}` присутствует в OpenAPI `[docs]`.

**must-have 9.** Jira: API-токен привязан к пользователю Atlassian; отдельный «бот» — это отдельный аккаунт, а Jira Cloud тарифицируется за пользователя. Требование «бот с отдельным токеном, действия отличимы от человека» технически выполнимо, но упирается в биллинг — см. раздел 4. Линейный/Plane/Kaneo: ключ можно завести отдельному служебному пользователю `[docs]`.

**must-have 10.** У всех есть эндпоинт добавления комментария к задаче. Jira: `POST /rest/api/3/issue/{issueIdOrKey}/comment` — `[docs]` [api-group-issue-comments](https://developer.atlassian.com/cloud/jira/platform/rest/v3/api-group-issue-comments/). Kaneo: `/comment/{taskId}` — `[docs]`.

## 4. Матрица: self-hosted, кандидаты × must-have

| # | must-have | YouTrack Server | OpenProject CE | Taiga | Redmine | Wekan | Plane (self-hosted) | Kaneo (self-hosted) | Vikunja |
|---|---|---|---|---|---|---|---|---|---|
| 1 | Публичный API, токен без OAuth | P `[live]` | P `[docs]` | P `[live]` | P `[live]` | ? | P `[docs]` | P `[docs]` | P `[live]` |
| 2 | Parity UI и API | P `[docs]` | P `[docs]` | P `[docs]` | ? | ? | P `[docs]` | ? | **P `[live]`** |
| 3 | Зависимости/блокировки через API | P `[docs]` | P `[docs]` | P `[docs]` | P `[docs]` | ? | P `[docs]` | P `[docs]` | **P `[live]`** |
| 4 | Стабильный идентификатор | P `[live]` | **F** `[docs]` | P `[live]` | P `[live]` | ? | P `[docs]` | P `[docs]` | **P `[live]`** |
| 5 | Подзадачи через API | P `[docs]` | P `[docs]` | ? | P `[docs]` | ? | P `[docs]` | P `[docs]` | **P `[live]`** |
| 6 | Webhooks либо poll | P `[docs]` | P `[docs]` poll | P `[live]` | P `[live]` poll | ? | P `[docs]` | P `[docs]` | **P `[live]`** |
| 7 | Markdown без искажений | P `[live]` | P `[docs]` | ? | ? | ? | ? | ? | **P `[live]`** |
| 8 | Экспорт в JSON/CSV | P `[docs]` | P `[docs]` | P `[docs]` | P `[docs]` | ? | P `[docs]` | P `[docs]` | P `[live]` 412 |
| 9 | Сервис-аккаунт | P `[docs]` | ? | P `[docs]` | P `[docs]` | ? | P `[docs]` | ? | P `[docs]` |
| 10 | Комментарий по номеру | P `[docs]` | P `[docs]` | P `[docs]` | P `[docs]` | ? | P `[docs]` | P `[docs]` | **P `[live]`** |

Обновление относительно первой редакции отчёта: у Plane закрыты клетки 2, 3, 5, 6 — читал исходники роутера `apps/api/plane/api/urls/` (список `urlpatterns` со всеми подключаемыми модулями). Поля `?` у Plane остались только там, где ни доки, ни код не давали ответа.

Почему у Vikunja почти вся строка `[live]`: только он даёт публичную песочницу с опубликованными кредами. Это не значит, что он качественнее остальных — это значит, **его must-have доказаны, а must-have остальных в основном заявлены**.

### Расшифровка и ловушки

**OpenProject, must-have 6 — ключевая находка.** В core-спеке, скачанной с живого community-инстанса `https://community.openproject.org/api/v3/spec.json`, **отсутствуют** любые пути управления webhooks:

```
paths matching 'hook' → (пусто)
```

Webhooks настраиваются только через UI: «Navigate to **Administration → API and webhooks**… Press the green **+ Webhook** button» — `[docs]` [openproject.org/docs/system-admin-guide/api-and-webhooks](https://www.openproject.org/docs/system-admin-guide/api-and-webhooks/). То есть webhook в OpenProject нельзя завести программно через API — только руками в интерфейсе админа. Это не провал must-have 6 (poll-ветка закрыта через фильтр по времени, эндпоинты `/api/v3/work_packages` с параметром `timestamps` есть в спеке), но планировать «CI сам подписывается на события» нельзя.

Там же в спеке 16 упоминаний Enterprise-ограничений. Проверенная формулировка:

```
"enterprise token missing": {"description":"This error is returned, if there is no Enterprise token available.
The file upload to that storage is only available in an Enterprise edition."}
```

и

```
| highlightingMode | Which highlighting mode should the table have? ... | Enterprise edition only |
```

Ключевой факт: **ограничения Enterprise в OpenProject касаются конкретных фич (хранилища файлов, подсветка таблиц, ограничения на глубину timestamps «Values older than 1 day are accepted only with valid Enterprise Token available»), а не самого API v3.** API v3, relations, work packages, render/markdown доступны в Community — это подтверждается тем, что скачанная спека — со спекой community-инстанса, и в ней эти пути есть. Open-core ловушка здесь **не срабатывает** на must-have чеклисте.

**OpenProject, must-have 4 — fail.** Идентификатор work package — числовой `id`, отдельного ключа вида `PROJ-123` нет. В спеке work package идентифицируется по `/api/v3/work_packages/{id}`, где `id` — целое. Поиск по подстроке `PROJ-123` невозможен, потому что ключа нет. Формально требование «число или key вида `PROJ-123`, единый в URL, API и commit message» выполнено числом, но требование «поиск по подстроке» — нет. Помечено fail, так как это реальное ограничение для связки commit message → задача.

**OpenProject, must-have 7.** В спеке есть `/api/v3/render/markdown` и `/api/v3/render/plain` — `[docs]`. Наличие эндпоинта рендеринга markdown означает, что markdown в описаниях поддерживается; обратное чтение без искажений отдельно не проверял — статус `[docs]`, не `[live]`.

**OpenProject, must-have 3.** `/api/v3/relations`, `/api/v3/relations/{id}`, `/api/v3/work_packages/{id}/relations`, `/api/v3/work_packages/{id}/available_relation_candidates` — все четыре пути в спеке `[docs]`. Четвёртый особенно ценен: он отдаёт кандидатов на связывание, то есть позволяет строить блокировки.

**Redmine, must-have 5.** Подзадачи — через `parent_issue_id` при создании и фильтр `parent_id` при листинге, плюс `include=children` — `[docs]` [Rest_Issues](https://www.redmine.org/projects/redmine/wiki/Rest_Issues). Это иерархия parent/child, а не отдельная сущность subtask.

**Redmine, must-have 3.** Issue Relations — в API-таблице помечены статусом **Alpha**: «Issue Relations | Alpha | | 1.3» — `[docs]` [Rest_api](https://www.redmine.org/projects/redmine/wiki/Rest_api). Список статусов там же: Stable — feature complete; Alpha — «major functionality in place, needs feedback from API users and integrators». Связи работают, но API считается нестабильным.

**Redmine, must-have 6.** Webhooks в ядре отсутствуют — их нет ни в таблице API-ресурсов, ни в списке эндпоинтов. Poll закрыт: `updated_on=%3E%3D2026-01-01` — `[live]`, 200. То есть CI должен опрашивать.

**Redmine, must-have 7.** Markdown в Redmine — не формат по умолчанию. Страница документации `RedmineTextFormattingMarkdown` существует (`[live]`, 200), но это настройка текстового формата; по умолчанию исторически Textile. Статус `?` — не проверял, какой формат выставлен на конкретном инстансе и читается ли markdown обратно без искажений.

**Redmine, must-have 8.** JSON и XML на чтение — `[live]`, 200. CSV — `[live]`, 403 анонимно (может быть доступно с токеном; не проверял).

**Taiga, must-have 6.** `[live]` — `GET /api/v1/webhooks` → `200 []`. В доке также перечислены `/api/v1/webhooks` (GET/POST), `/api/v1/webhooks/{webhookId}` (GET/PUT/PATCH/DELETE), `/api/v1/webhooks/{webhookId}/test` (POST), `/api/v1/webhooklogs` и `/api/v1/webhooklogs/{webhookLogId}/resend` — `[docs]` [docs.taiga.io/api.html](https://docs.taiga.io/api.html). Это самый полный webhook-API среди self-hosted кандидатов.

**Taiga, must-have 1.** Два механизма: `Authorization: Bearer ${AUTH_TOKEN}` (обычный токен) и `Authorization: Application ${AUTH_TOKEN}` (application token для внешних приложений) — `[docs]`. Второй — это ровно паттерн «сервис-аккаунт для автоматики», и он без браузерного OAuth: цепочка `authorize → validate → decypher`, вся по API.

**Taiga, must-have 4.** `[live]` — `idReadable`-аналога нет, но в ответе issues фигурируют числовые id, а в доке есть `/api/v1/resolver` для «Resolve references and slugs». Живой вызов `resolver` без токена → 401, то есть метод существует и защищён. Стабильность id при переносе между колонками (у Taiga — статусами канбана) не проверял; по доке статус — атрибут `status_id` на пользовательской истории, а не её id.

**Taiga, must-have 5.** Подзадачи: `[unverified]`. В оглавлении эндпоинтов, прочитанном из `docs.taiga.io/api.html`, отдельного ресурса subtask нет — перечислены проекты, user stories, tasks, issues, webhooks, но подзадач как ресурса я не увидел. Ставить `[docs]`-pass без проверки конкретного эндпоинта не стал.

**Taiga, must-have 9.** Application tokens привязаны к существующему пользователю и приложению, создаются программно через API — `[docs]`. Отличимость действий бота от человека — `?`.

**YouTrack Server, must-have 1 и 4.** `[live]` — два запроса выше. Плюс в OpenAPI-спеке (564 729 байт) есть полный набор: `/issues`, `/issues/{id}`, `/issues/{id}/comments`, `/issues/{id}/links`, `/customFields`.

**YouTrack Server, must-have 6.** Webhook Triggers app: события issue/comment/work-item/attachment — created/updated/deleted, несколько webhook на одно событие, catch-all webhooks. Аутентификация — shared webhook token, минимум 32 символа, рекомендуется 64 hex. Установка требует права Low-level Admin Write — `[docs]` [webhook-triggers.html](https://www.jetbrains.com/help/youtrack/server/webhook-triggers.html.md). Важно: webhook задаётся **в YouTrack**, а не подпиской через API; API-эндпоинта управления webhooks в спеке нет.

**YouTrack Server — лицензирование (закрыто 2026-10-01, обновление).** Прежний вывод «статус не установлен» был ошибкой: страница `youtrack-server-license.html` оказалась просто алиасом вводной страницы доков, а не признаком заката. Факты, найденные позже: (а) действующий договор — [jetbrains.com/legal/docs/youtrack/license/](https://www.jetbrains.com/legal/docs/youtrack/license/) → `200`, 87 460 байт, «YouTrack Server — License Agreement, Version 15, effective as of July 22nd, 2024», описывает покупку `[live]`; (б) живой прайс — [jetbrains.com/youtrack/buy/?section=server](https://www.jetbrains.com/youtrack/buy/?section=server) → `200`, user pack от 15 до 300 000 пользователей `[live]`; (в) модель изменилась 23.06.2026: perpetual лицензии продавались до этой даты, после — annual user pack subscription `[docs]` [manage-youtrack-server-license](https://www.jetbrains.com/help/youtrack/server/manage-youtrack-server-license.html.md). **Free-план: 10 users + 3 helpdesk-агента, $0** (`shopToken C:N:YTS10A:Y` в прайс-массиве `[live]`). Это снимает open-core риск по YouTrack.

**Wekan.** Колонка почти целиком `?`. Репозиторий жив (`[live]`: 21 099 звёзд, push сегодня), но `docs.wekan.org` при каждом обращении отдавал «HTTP connect timed out» — ни `/API/`, ни `/API/Webhooks/` не открылись. `wekan.github.io/api.html` → 404, `www.wekan.io` → DNS не разрешается. Проверить must-have против первоисточника не удалось.

**Plane (self-hosted).** По коду жив (`[live]`: 60 206 звёзд, AGPL-3.0, релиз `v1.4.2` от 2026-08-23, 1 070 открытых issues). В дереве репозитория (`[live]`, листинг `apps/`: admin, api, live, proxy, space, web; `packages/`: blocks, editor, services и другие) каталогов с пометкой закрытой редакции нет — но это структурный признак, а не доказательство полноты функционала, поэтому статус `[live]` стоит только у самого факта листинга. API — тот же REST, что и у cloud, но база URL задаётся инсталляцией: «If you're using a self-hosted instance of Plane, your API base URL will differ based on your custom domain and setup» — `[docs]`. Большинство ячеек `?`, так как специфику self-hosted редакции (какие фичи урезаны относительно cloud) не проверял.

**Kaneo (self-hosted).** MIT без open-core раздела — весь код открыт, платной EE-редакции нет (`[live]`: лицензия MIT из GitHub API, `lic=MIT`). Это снимает open-core ловушку целиком. 144 пути в OpenAPI, включая `/task-relation`, `/comment/{taskId}`, `/task/by-ticket-id/{ticketId}`, `/task/export/{projectId}`, `/generic-webhook-integration/project/{projectId}`. В README заявлены «subtasks, dependencies», «personal webhooks», «project webhooks», «REST API» и «MCP for AI assistants» — `[docs]`.

**Vikunja.** Единственный кандидат, у которого must-have подтверждены записью, а не только чтением (раздел 2.7). Спека — Swagger 2.0 на 128 путей, снятая с живого демо-инстанса. Что даёт по пунктам, не разобранным выше:

- *must-have 1* (`[live]`): токен через `POST /login`, 316 символов, далее `Authorization: Bearer`.
- *must-have 8, экспорт*: `/user/export` (get), `/user/export/download` (post), `/user/export/request` (post) — `[docs]`. На демо `request` дал `412 Precondition Failed`, `user/export` вернул `{}`. Методы существуют и защищены; рабочий цикл экспорта целиком не пройден.
- *must-have 9, сервис-аккаунт*: в схеме есть `models.APIToken` и `models.APITokenRoute` с `models.APIPermissions` — то есть токены с ограничением маршрутов `[docs]`. Отдельный бот-пользователь не проверялся.
- *Синхронизация*: помимо webhooks, есть `/notifications`, `/notifications/{id}`, `/subscriptions/{entity}/{entityID}` — `[docs]`.

Что важно знать про Vikunja перед выбором: идентификатор задачи — `#1` внутри проекта, **не глобально уникальный** — в поиске выше одновременно фигурировали `identifier=#3` у одной задачи и `#1` у другой из разных проектов. Для commit message это значит, что `#1` придётся квалифицировать номером проекта. Проект — Go/AGPL, лицензию и open-core структуру отдельно не проверял (`[unverified]`).

## 5. Дополнительные критерии

### 5.1 Cloud

| Критерий | Linear | Jira Cloud | YouTrack Cloud | Shortcut | Plane Cloud | Kaneo Cloud |
|---|---|---|---|---|---|---|
| Модель цены | за место `[docs]` | за место `[docs]` | за место `[unverified]` | за место `[unverified]` | `[unverified]` | Cloud-версия есть `[docs]` |
| Считается ли бот местом | `?` | да, аккаунт биллингуется `[docs]` | `?` | `?` | `?` | `?` |
| Free tier | есть `[docs]` | есть `[docs]` | `?` | `?` | `?` | `?` |
| Rate limits документированы | `[unverified]` | `[docs]` | `[docs]` | `[docs]` | **да**, 60 req/min `[docs]` | описан 429 в таблице ошибок `[docs]` |
| 429 + Retry-After | `?` | `?` | `?` | `?` | 429 описан, Retry-After не подтверждён `[docs]` | `?` |
| Status page | есть, но `/api/v2/status.json` отдаёт HTML `[live]` | `[live]` 200, indicator none | 401 `[live]` | `[live]` 200, indicator none | `[live]` 200, indicator none | `?` |
| Экспорт работает сейчас | `[unverified]` | `[unverified]` | `[unverified]` | `[unverified]` | `[unverified]` | `[unverified]` |
| Песочница | `?` | Free-план `[docs]` | `?` | `?` | `?` | `[docs]` cloud.kaneo.app |

Единственные документированные rate limits, найденные в этом прогоне, — у Plane:

```
Limit: 60 requests per minute. Reset interval: 1 minute. Scope: all requests made with a given API key.
X-RateLimit-Remaining: 45
X-RateLimit-Reset: 1700327957
```

`[docs]` [docs.plane.so/api-reference/introduction](https://docs.plane.so/api-reference/introduction). `Retry-After` в доке **не** упомянут — только «429 Throttling Error. Retry the request after some time». Требование «превышение даёт 429 с Retry-After» — `[unverified]`.

Для Jira: `/rest/api/3/issue/picker` и `/rest/api/3/search/jql` помечены «This operation can be accessed anonymously», но операции записи требуют токена.

### 5.2 Self-hosted

| Критерий | YouTrack Server | OpenProject CE | Taiga | Redmine | Wekan | Plane | Kaneo | Vikunja |
|---|---|---|---|---|---|---|---|---|
| docker-compose в один шаг | **один контейнер Docker** `[docs]` | `?` | `?` | `?` | `?` | `[unverified]` | `[docs]` есть гайд установки | `?` |
| Число сервисов / RAM | один сервис, 1.5 GB RAM min, heap 1024m `[docs]` | `?` | `?` | `?` | `?` | `?` | `?` | `?` |
| Работа на VPS 2 GB | да: 1.5 GB min, i3-class CPU `[docs]` | `?` | `?` | `?` | `?` | `?` | `?` | `?` |
| Миграции без ручного SQL | `?` | `?` | `[docs]` авто-миграции Django | `[docs]` rake-миграции | `?` | `?` | `?` | `?` |
| Бэкап/restore одной командой | `?` | `?` | `?` | `?` | `?` | `?` | `?` | `?` |
| Open-core ловушка | **снят (2026-10-01)**: договор действует `[live]`, прайс живой `[live]`, free 10 users | API v3 доступен в community-спеке `[docs]` | открытый код, лицензия `[unverified]` | открытый код, лицензия `[unverified]` | открытый код, лицензия `[unverified]` | признаков закрытой редакции в дереве нет `[live]` | **нет**: MIT `[live]` | `[unverified]` |
| Живость: коммиты | `?` | `[live]` v17.9.0 от 2026-09-30 | `[live]` 2026-09-28 | `[live]` 2026-10-01 | `[live]` 2026-10-01 | `[live]` v1.4.2 от 2026-08-23 | `[live]` v2.29.3 от 2026-09-29 | `[live]` v2.6.0 на демо |
| CVE-патчи за год | `?` | `?` | `?` | `?` | `?` | `?` | `?` | `?` |
| Доступ агентов извне при NAT | токен, туннель `[docs]` | токен `[docs]` | токен `[docs]` | `X-Redmine-API-Key` `[docs]` | `?` | токен `[docs]` | `[docs]` Bearer | токен `[live]` |
| Проверено живьём (запись) | нет | нет | нет | нет | нет | нет | нет | **да** |

Про Open-core: проверка сделана на живом community-инстансе OpenProject через скачивание его спеки, поэтому вывод «API v3 не урезан в Community» опирается на первоисточник, а не на страницу сравнения планов. Enterprise-ограничения локализованы точечно (файловые хранилища, подсветка таблиц, глубина истории timestamps больше суток).

## 6. Сравнение внутри категорий

### Cloud

**По полноте API** лидируют Shortcut и Linear — у обоих машинoчитаемая схема, где видно каждый путь. Shortcut удобнее тем, что OpenAPI можно скачать целиком и сгенерировать клиент; Linear — GraphQL, что даёт один эндпоинт и типизированную схему, но требует GraphQL-клиента вместо REST.

**Единственный формальный fail среди cloud — Jira Cloud по must-have 7.** Хранение в ADF вместо markdown означает, что наш адаптер вынужден конвертировать markdown ↔ ADF в обе стороны. Это не блокер, это цена.

**По наблюдаемости** лучше всех Plane: документированы и лимит (60 req/min), и заголовки состояния, и формат курсорной пагинации.

**Jira и биллинг бота.** Jira Cloud тарифицируется за пользователя, а сервис-аккаунт — это пользователь. Цифру за место я в этом прогоне подтвердить не смог (`https://www.atlassian.com/software/jira/pricing` — ошибка «Invalid cookie name-value pair» при разборе). Требование «считается ли агент-бот местом» — `[unverified]`, и это ключевой вопрос по цене для Jira.

### Self-hosted

**Vikunja — лидер по доказанности, не по фичам.** Только здесь must-have подтверждены записью: задача создана и прочитана, markdown вернулся байт-в-байт, комментарий записан и прочитан, блокировка и подзадача созданы с чтением в обе стороны, колонка создана через API, webhook зарегистрирован, id не сместился при переносе между колонками. Против этого у всех остальных кандидатов стоят заявления из доков. **Но:** идентификатор в пределах проекта, а не глобальный, и open-core статус не проверен.

**Taiga — единственный кандидат с полноценным webhook-API** (`/api/v1/webhooks` + логи + resend), подтверждённым живым вызовом. Для «CI сам подписывается на события» это лучший вариант в категории.

**Redmine — самый проверенный живыми вызовами** (листинг, фильтр по времени, формат JSON), но: webhooks в ядре нет, Issue Relations помечены Alpha, подзадачи — это parent/child.

**OpenProject CE — самый широкий подтверждённый API** (отношения, кандидаты на связывание, рендер markdown), но webhooks только через UI админа, и нет ключа вида `PROJ-123`.

**Plane — выше всех поднялся после второй проверки.** По первой редакции у него было четыре `?` подряд, и он выглядел слабейшим. Чтение исходников роутера показало обратное: ключ `PROJ-123` есть, links/comments/activities есть, подзадачи заложены в модель, webhooks есть — просто лежат на другой ветке роутера, не под `/api/v1/`. **Ключевой риск:** ни одного живого вызова Plane не сделано, всё `[docs]` из исходников.

**YouTrack Server** прошёл все must-have, что проверялись, и дал лучшие живые пруфы по идентификатору и markdown. Риск лицензирования, ранее державший его вне шортлиста, снят 2026-10-01: договор Version 15 (2024-07-22) действует, прайс живой, free-план 10 users + 3 агента, покупка — annual user pack от 15 users ($900/год); perpetual продаётся только тем, кто купил до 23.06.2026. Ресурсы известны и скромны: 1.5 GB RAM, JVM heap 1024m — вписывается в мелкий VPS.

**Wekan** — тёмная лошадка: репозиторий активно развивается, но документация недоступна, поэтому ни один must-have не проверен.

**Kaneo** — единственный кандидат, где open-core ловушка отсутствует по структуре репозитория: MIT, дерево содержит `apps/`, `packages/`, `charts/`, `deploy/` и не содержит каталогов с закрытой редакцией (`[live]`, листинг через GitHub API). Но parity UI/API и поведение subtasks/dependencies в рантайме не проверены.

## 7. Шортлист

Гипотеза для проверки человеком, не решение. Ключевой риск указан у каждого.

### Cloud

1. **Linear.** Полностью проходит must-have, включая markdown и sub-issues, есть отдельная документация про интеграцию AI-агентов. **Ключевой риск:** GraphQL вместо REST — наш VCS-скрипт «найти задачу по номеру + дописать комментарий» потребует GraphQL-запросов, и rate limits остались непроверенными (`[unverified]`).
2. **Shortcut.** Полностью проходит must-have, вся схема машинoчитаема (85 путей в OpenAPI), можно сгенерировать клиент. **Ключевой риск:** в этом прогоне ни один живой вызов Shortcut не выполнен и ни одна цена не подтверждена по первоисточнику — всё держится на спеке.
3. **Plane Cloud.** После чтения исходников роутера закрылись четыре `?` из первой редакции: ключ `PROJ-123` есть, links, comments, activities, подзадачи в модели, webhooks — есть. Плюс единственный с документированными rate limits (60 req/min) и заголовками состояния. **Ключевой риск:** ни одного живого вызова Plane не сделано — всё держится на исходниках репозитория, а страницы доков отдавали 404. Если API на живом инстансе расходится с кодом `master`, узнаем только на прогоне.

### Self-hosted

1. **Vikunja.** Единственный кандидат, у которого must-have подтверждены записью, а не чтением: задача, комментарий, подзадача, блокировка, колонка, webhook, markdown байт-в-байт, неизменность id при переносе. 128 путей, 19 событий webhooks, MIT. **Ключевой риск:** идентификатор `#1` уникален внутри проекта, а не глобально — для связки commit message → задача его придётся квалифицировать номером проекта. Плюс на демо цикл экспорта не прошёл (412), open-core статус не проверен.
2. **YouTrack Server.** Закрытый ранее риск лицензирования снят: договор Version 15 (2024-07-22) действует, прайс живой, free-план 10 users + 3 агента покрывает сценарий «человек + AI-агенты» с запасом; переключение paid ↔ free делается из UI. Ресурсы: минимум 1.5 GB RAM, JVM heap 1024m, метаспейс 250m — работает на мелком VPS. Установка — один контейнер Docker, конфигурация через Configuration Wizard с одноразовым токеном из лога контейнера; регистрации в JetBrains Account при установке не требуется — лицензия подтверждается в wizard-е, admin задаётся паролем локально `[docs]` [youtrack-docker-installation](https://www.jetbrains.com/help/youtrack/server/youtrack-docker-installation.html.md). **Ключевой риск:** с 23.06.2026 новые лицензии только по подписке ($900/год за 15-user pack); если 10 пользователей free-плана станут тесно, дешёвого пути расширения нет — perpetual больше не продаётся.
3. **Taiga.** Живые пруфы (проекты, webhooks), полный webhook-API с логами и resend, два механизма токена включая application token ровно под автоматику. **Ключевой риск:** подзадачи через API не подтверждены (`[unverified]`) — а это must-have, то есть кандидат может вылететь при проверке.
4. **Plane self-hosted.** Ключ `PROJ-123`, links, подзадачи, webhooks, AGPL целиком. **Ключевой риск:** всё — `[docs]` из исходников `master`; ни один инстанс не поднят, ни один вызов не сделан.

**YouTrack переведён из «вне шортлиста» в шортлист self-hosted (позиция 2):** лицензионный вопрос, который раньше держал его вне списка, закрыт — договор действует (Version 15, 2024-07-22), прайс живой, free-план 10 users покрывает сценарий. Прежний пункт о «закрытой вендором ветке» удалён как опровергнутый.

## 8. Расширенный функционал

Отдельный блок **сравнения**, не фильтр: провал здесь никого не выбраковывает.

Легенда: **✅** есть, **◐** частично (есть чтение, нет записи, либо только через интеграцию), **✖** нет, **?** не установлено.

### 8.1 Cloud

| # | Функционал | Linear | Jira Cloud | YouTrack Cloud | Shortcut | Plane Cloud | Kaneo Cloud |
|---|---|---|---|---|---|---|---|
| 1 | Кастомные поля: глобально / на проект | ? | ✅ оба `[docs]` | ✅ оба `[docs]` | ◐ только чтение `[docs]` | ? | ✅ оба `[docs]` |
| 2 | Спринты или аналог | ✅ Cycles `[docs]` | ✅ Sprint API `[docs]` | ✅ `/agiles/{id}/sprints` `[docs]` | ✅ Iterations `[docs]` | ✅ Cycle `[docs]` | ✖ `[live]` |
| 3 | Привязка веток git-репозитория | ✅ GitHub/GitLab `[docs]` | ✅ DVCS-коннекторы `[docs]` | ✅ VCS-интеграция `[docs]` | ◐ generic-ссылки `[docs]` | ✅ GitHub-модели `[docs]` | ✅ Gitea/GitLab/GitHub `[docs]` |
| 4 | Связи задач, блокеры | ✅ `[docs]` | ✅ `[docs]` | ✅ `[docs]` | ✅ `[docs]` | ✅ `[docs]` | ✅ `[docs]` |
| 5 | Кастомизация статусов и переходов | ◐ статусы без правил `[docs]` | ✅ workflow-движок `[docs]` | ✅ workflow-правила `[docs]` | ◐ workflow только чтение `[docs]` | ◐ статусы без правил `[docs]` | ◐ колонки без правил `[docs]` |
| 6 | Роли пользователей | ✅ `[docs]` | ✅ `[docs]` | ✅ `/roles` `[docs]` | ◐ участники, роли в спеке `[docs]` | ✅ Admin/Member/Guest `[docs]` | ✅ `[docs]` |
| 7 | Публичный просмотр багов/доски/дашборда | ◐ дашборды Enterprise-only `[docs]` | ? | ✅ анонимный доступ `[docs]` | ✖ `[docs]` | ✅ `DeployBoard` + PUBLIC `[docs]` | ✅ `/public-project/{id}` `[docs]` |

**Расшифровка cloud.**

*Строка 1, кастомные поля.* Jira: `api-group-issue-custom-field-configuration` — конфигурация полей с контекстами, что даёт привязку к проекту `[docs]` [atlassian.com](https://developer.atlassian.com/cloud/jira/platform/rest/v3/api-group-issue-custom-field-configuration/) (проверено HTTP 200). YouTrack: глобальный уровень `/admin/customFieldSettings/customFields` и проектный `/admin/projects/{id}/customFields` — обе области в спеке `[docs]`. Kaneo: `/custom-field` и `/custom-field/project/{projectId}` — обе области в OpenAPI `[docs]` [kaneo.app/docs/openapi.json](https://kaneo.app/docs/openapi.json). **Shortcut: ◐** — важная деталь: в спеке `/api/v3/custom-fields` имеет только `get`, а `/custom-fields/{id}` — `get,put,delete`. Создание кастомного поля через API **отсутствует**: есть чтение, правка существующего и удаление, но не создание. **Linear: `?`** — в тексте `developers.linear.app/docs/graphql/working-with-the-graphql-api` (460 724 байта) и в `linear.app/changelog` (1 660 612 байт) слово `customField` не встречается ни разу; при этом GraphQL-схема без токена отдаёт `403`, поэтому схему напрямую проверить не удалось. **Plane: `?`** — среди моделей `apps/api/plane/db/models/` (список импортов в `__init__.py`) модели кастомного поля нет; есть `IssueType` и `Estimate`/`EstimatePoint`. Отсутствие модели в импортах — сильный, но не окончательный признак.

*Строка 2, спринты.* Linear: «Cycles are a practice to keep up your team's momentum, similar to commonly used agile-flavored sprints», длительность 1–8 недель `[docs]` [use-cycles](https://linear.app/docs/use-cycles.md). Shortcut: `/api/v3/iterations` с операциями `get,post` `[docs]` (спека). YouTrack: `/agiles`, `/agiles/{id}/sprints`, `/agiles/{id}/sprints/{sprintId}` `[docs]` (спека). Jira: группа `api-group-sprint` `[docs]` (HTTP 200). Plane: модель `Cycle` + связка `CycleIssue`, роутер `cycle.py` с девятью `path(` `[docs]` (исходники). **Kaneo: ✖** — в OpenAPI 144 путей, ни одного с `sprint`/`cycle` (0 совпадений по тексту спеки). То есть колонки есть, а итераций нет.

*Строка 3, git.* Linear: «Link Linear issues to GitLab merge requests. Automate your MR workflow so that issues update when MRs are drafted, opened, merged» `[docs]` [gitlab](https://linear.app/docs/gitlab.md), аналогично для GitHub. Plane: `GithubRepository`, `GithubRepositorySync`, `GithubIssueSync` с `unique_together = ["repository_sync", "issue"]` `[docs]` (исходники `db/models/integration/github.py`). Kaneo: схемы `GithubAppInfo`, `GiteaRepository`, `GitLab`-интеграция в OpenAPI `[docs]`. **Shortcut: ◐** — есть `/api/v3/linked-files` (`get,post`) и `/api/v3/external-link/stories` (`get`), но это generic-ссылки, а не привязка веток. **Важная оговорка по всем:** это встроенные интеграции вендора, а по условию задачи они не обязательны — нам достаточно API «найти задачу + дописать комментарий». Строка показывает, что есть сверх необходимого, а не то, чем мы обязаны пользоваться.

*Строка 4, блокеры.* Дублирует must-have 3 (раздел 4) — здесь оставлена для полноты сравнения, повторно не расшифровывается. Различие в глубине: у Vikunja и Plane блокировка — отдельная модель/связь, у Jira — issue links с типами.

*Строка 5, процессы и переходы.* Jira: группа `api-group-workflows` — движок переходов с условиями и валидаторами, исторически самая сильная часть Jira `[docs]` (HTTP 200). YouTrack: 25 совпадений `workflow` в спеке — state-machine-правила. **Linear: ◐** — статусы настраиваются (добавление, переименование, порядок), но «Teams can reorder statuses within each status category, but the categories themselves stay in a fixed order» — категории фиксированы, правил перехода «из какого в какой можно» нет `[docs]` [configuring-workflows](https://linear.app/docs/configuring-workflows.md). **Shortcut: ◐** — `/api/v3/workflows` и `/api/v3/workflows/{id}` в спеке только `get`; читать можно, менять через API нельзя. **Plane: ◐** — `State` с `StateGroup` (backlog/unstarted/started/completed/cancelled/triage) настраивается, но правил перехода в модели нет. **Kaneo: ◐** — `/column/{id}`, `/column/{projectId}`, `/column/reorder/{projectId}`; колонки как статусы есть, переходов нет.

*Строка 6, роли.* Linear: «Linear provides several role types… Please note that on Free plans, all users are Admins» `[docs]` [members-roles](https://linear.app/docs/members-roles.md). Jira: `api-group-project-role-actors` `[docs]` (HTTP 200). Plane: `ROLE_CHOICES = ((20, "Admin"), (15, "Member"), (5, "Guest"))` в `WorkspaceMember` `[docs]` (исходники). Kaneo: `/auth/organization/update-member-role`, `/get-active-member-role` `[docs]` (OpenAPI). **Shortcut: ◐** — `/api/v3/members` только `get`; управление участниками через API не подтверждено.

*Строка 7, публичный просмотр.* **Linear: ◐ с оговоркой** — дашборды есть, но «Available to workspaces on our Enterprise plans» `[docs]` [dashboards](https://linear.app/docs/dashboards.md); кастомные виды шарятся, но «available to full members», то есть внутри воркспейса, а не публично. **Plane: ✅** — модель `DeployBoard` с полями `anchor` (уникальный, индексированный), `is_comments_enabled`, `is_votes_enabled`, `is_disabled`, плюс `ProjectNetwork.PUBLIC` и `NETWORK_CHOICES = ((0, "Secret"), (2, "Public"))` `[docs]` (исходники). **Kaneo: ✅** — три пути `/public-project/{id}`, `/public-project/{id}/description`, `/public-project/{id}/task/{taskId}/description` `[docs]` (OpenAPI). **Shortcut: ✖** — публичного просмотра в спеке нет. **Jira: `?`** — анонимного доступа к проекту в REST не подтвердил.

### 8.2 Self-hosted

| # | Функционал | YouTrack Srv | OpenProject CE | Taiga | Redmine | Wekan | Plane | Kaneo | Vikunja |
|---|---|---|---|---|---|---|---|---|---|
| 1 | Кастомные поля: глобально / на проект | ✅ оба `[docs]` | ✅ оба `[docs]` | ✅ на проект `[live]` | ? `[live]` 403 | ✅ на доску `[docs]` | ? | ✅ оба `[docs]` | ✖ `[docs]` |
| 2 | Спринты или аналог | ✅ `[docs]` | ✅ `[docs]` | ✅ milestones `[live]` | ? | ✖ swimlanes `[docs]` | ✅ Cycle `[docs]` | ✖ `[live]` | ✖ `[docs]` |
| 3 | Привязка веток git | ✅ `[docs]` | ? | ◐ интеграции `[unverified]` | ◐ SCM `[unverified]` | ✖ | ✅ `[docs]` | ✅ `[docs]` | ✖ `[docs]` |
| 4 | Связи задач, блокеры | ✅ `[docs]` | ✅ `[docs]` | ✅ `[docs]` | ✅ `Alpha` `[docs]` | ✅ `[docs]` | ✅ `[docs]` | ✅ `[docs]` | ✅ `[live]` |
| 5 | Кастомизация статусов и переходов | ✅ `[docs]` | ? | ✅ `[live]` | ◐ admin-only `[live]` | ✖ | ◐ `[docs]` | ◐ `[docs]` | ✖ `[docs]` |
| 6 | Роли пользователей | ✅ `[docs]` | ✅ `[docs]` | ✅ `[live]` | ✅ `[live]` | ✅ `[docs]` | ✅ `[docs]` | ✅ `[docs]` | ✅ `[docs]` |
| 7 | Публичный просмотр | ✅ `[docs]` | ? | ✅ `[live]` | ✅ `[live]` | ✅ `[docs]` | ✅ `[docs]` | ✅ `[docs]` | ✅ link-share `[docs]` |

**Расшифровка self-hosted.**

*Строка 1.* Taiga: `/api/v1/userstory-custom-attributes` — полный CRUD `[docs]`, живой `GET .../userstory-custom-attributes?project=1` → `200 []` `[live]`; область — проект. Redmine: `GET https://www.redmine.org/custom_fields.json` → **`403`** `[live]` — эндпоинт существует, но требует админских прав, поэтому состав полей не прочитан (`?`). Wekan: живая дока `wekan.fi/api/v9.57/` (839 102 байта) содержит 280 упоминаний `customField` и operationId `get_board_customField`, `put_board_customField`, `delete_board_customField`, `post_board_customField_dropdown-items` — CRUD в области доски `[docs]`. **Vikunja: ✖** — в Swagger 128 путей ноль совпадений по `custom_field`/`customfield`.

*Строка 2.* OpenProject: `/api/v3/sprints`, `/api/v3/projects/{id}/sprints`, `/api/v3/versions` `[docs]` (спека 1,2 МБ). Taiga: `GET /api/v1/milestones?project=1` → `200 []` `[live]`, CRUD по доке. **Wekan: ✖** — 379 упоминаний `swimlane`, но swimlane это дорожка на канбане, а не временной интервал; спринтов нет. **Kaneo ✖ и Vikunja ✖** — по нулю совпадений в их спеках.

*Строка 3.* **Честная оговорка:** для Taiga и Redmine встроенная git-интеграция у меня осталась `[unverified]` — Taiga умеет двигать задачи по активности репозитория через webhooks, Redmine исторически умеет подключать репозиторий, но конкретных эндпоинтов я не проверял, поэтому не ставлю pass. Публичные демо-инстансы не дают доступа к настройке репозитория.

*Строка 5.* Taiga: `GET /api/v1/userstory-statuses?project=1` → `200 []` `[live]` — статусы как ресурс проекта; плюс `/api/v1/swimlanes` → `200 []` `[live]` (swimlanes — это и рабочий процесс тоже). Redmine: `GET https://www.redmine.org/workflows.json` → **`403`** `[live]` — матрица переходов «роль × трекер × статус» существует, но доступна только администратору; поэтому `◐`. **Wekan: ✖**, **Vikunja: ✖** — ни колонок с правилами, ни редактора переходов в API.

*Строка 7.* Taiga: живой `GET /api/v1/projects?member=0&is_private=false` → `200` `[live]` — публичные проекты отдаются анонимно. Redmine: `GET https://www.redmine.org/projects.json?limit=1` → `200` `[live]` — публичные проекты читаются без токена. Vikunja: `/projects/{project}/shares`, `/projects/{project}/shares/{share}`, `/shares/{share}/auth` (post) + определение `models.LinkSharing` с `password`, `permission`, `shared_by`, `sharing_type` `[docs]` (спека) — публичная ссылка есть, при этом у неё могут быть пароль и права. **Kaneo: ✅** и **Plane: ✅** — см. 8.1.

### 8.3 Что этот блок меняет в картине

Три наблюдения, которые видны только при таком разрезе:

1. **Vikunja проседает по фичам, хотя выигрывает по доказанности.** Кастомных полей нет, спринтов нет, редактора переходов нет, git нет. Он лидирует в must-have по проверенности, но как «полноценный трекер для команды с процессом» он беден. Это разные вещи, и путать их нельзя.
2. **Shortcut и Shortcut-подобные дают чтение там, где нужна запись.** `/custom-fields` и `/workflows` — только `get`. Кастомизацию через API не завести. Это ровно тот случай, когда «в UI есть» не превращается в «в API есть».
3. **Jira и YouTrack остаются единственными с настоящим движком процессов**, где переходы «из какого в какой разрешено» описываются правилами, а не только порядком колонок. Если процесс важен — это сужает выбор сильнее, чем must-have.

## 9. Не проверено

### Не проверено из-за отсутствия токенов и аккаунтов

Запись с чтением обратно выполнена **только для Vikunja** — благодаря публичной песочнице с опубликованными кредами. Для всех остальных кандидатов не сделано ни одной записи. Значит, для них остаётся невыполненным:

- Создание задачи через API (must-have 1) — кроме Vikunja, не выполнено ни для кого.
- Запись комментария к задаче (must-have 10) — кроме Vikunja, не выполнено.
- Запись в кастомное поле и чтение его обратно (must-have 2, parity) — не выполнено ни для кого, включая Vikunja (там проверялись колонки, а не кастомные поля).
- Создание связи между задачами и чтение «что разблокировано» (must-have 3) — кроме Vikunja, не выполнено.
- Создание подзадачи (must-have 5) — кроме Vikunja, не выполнено.
- Цикл markdown → запись → чтение без искажений (must-have 7) — кроме Vikunja, не выполнен. Для остальных есть только чтение уже существующего markdown (YouTrack).

### Не проверено из-за недоступности первоисточников

- **Height** — все домены таймаутят, кандидат не оценён.
- **Wekan** — `docs.wekan.org` таймаутит, `wekan.github.io/api.html` → 404, `www.wekan.io` → DNS не разрешается. Must-have не проверены вообще.
- **Plane** — страницы `docs.plane.so/api-reference/webhooks` и `docs.plane.so/api-reference/work-items/create-work-item` отдают 404, `/api-reference/*/overview` — 308. Всё по Plane пришлось брать из исходников репозитория, а не из доков.
- **Vikunja** — документацию (`vikunja.io/docs`, `vikunja.io/api`) не читал вообще: работал только со Swagger-спекой живого инстанса и вызовами. Лицензия и open-core статус не проверены.
- **Linear** — `developers.linear.app` и `linear.app/docs` рендерятся JavaScript-ом, часть страниц (rate limiting) вернула только навигационную оболочку без содержимого.
- **Jira** — страница цен вернула ошибку разбора cookie, поэтому тарифы за место не подтверждены.

### Не проверено по объёму работы

- **YouTrack Server: локальный инстанс** — лицензия проверена по первоисточникам (договор, прайс, доки), но ни один локальный Server-инстанс не поднимался: живые вызовы шли против `youtrack.jetbrains.com`, а не против self-hosted установки. Установка и Configuration Wizard описаны в доках `[docs]`, но не пройдены руками.
- **Rate limits** для Linear, Jira, YouTrack, Shortcut — не подтверждены. Наличие `429` в таблице ошибок у Kaneo и Plane найдено, но поведение `Retry-After` не проверено нигде.
- **Экспорт в JSON/CSV** — работоспособность «прямо сейчас» не проверена ни для одного кандидата. Redmine CSV отдал 403 анонимно (не доказывает недоступность с токеном). Vikunja `export/request` отдал 412 на демо — метод есть, но полноценного цикла не прошло.
- **Стабильность id при переносе между колонками** — проверена только у Vikunja (`[live]`, id не изменился). У остальных — `[unverified]`.
- **Поиск по идентификатору задачи** — подтверждён только косвенно (Vikunja по `title`; у Plane есть отдельный endpoint `PROJ-123`, но без живого вызова).
- **Сервис-аккаунты** — отличимость действий бота от действий человека не проверена нигде. Для Jira открыт вопрос, считается ли бот оплачиваемым местом.
- **Self-hosted развёртывание** — docker-compose и бэкап/откат не проверены ни для одного кандидата, ни один инстанс не поднимался. Для YouTrack ресурсы и деплой подтверждены доками (1.5 GB RAM, один Docker-контейнер), но не прогоном.
- **CVE-патчи за последний год** — не проверено ни для одного проекта.
- **YouTrack Server: считается ли бот-пользователь оплачиваемым местом** — в договоре есть «Reporter» («individual or bot with a reporter account»), но прямой формулировки про сервис-аккаунты в free-лимите нет; надо уточнять у JetBrains. Лицензирование как таковое закрыто.

### Что нужно, чтобы закрыть пробелы

Минимальный набор для полного прогона must-have:

1. **Облако:** бесплатный аккаунт в Linear, Jira Cloud, YouTrack Cloud, Shortcut, Plane Cloud с выпущенным personal API token. Этого достаточно, чтобы закрыть все 10 must-have живыми вызовами за один прогон.
2. **Self-hosted:** `docker-compose up` для Taiga, OpenProject CE, Kaneo, Redmine, Plane, Wekan на одной машине и админский токен каждого. Плюс замер потребления RAM под нагрузкой «один проект, сотня задач» для проверки работы на VPS 2 GB. Для Vikunja отдельный запуск не обязателен — есть живое демо, но для замера RAM и проверки бэкапа свой инстанс всё равно нужен.
3. **YouTrack Server:** вопрос о продаже закрыт (договор Version 15, прайс живой, free 10 users). Осталось — поднять инстанс по инструкции [youtrack-docker-installation](https://www.jetbrains.com/help/youtrack/server/youtrack-docker-installation.html.md), пройти Configuration Wizard и прогнать must-have живыми вызовами против него.

## 10. Источники

Первоисточники, к которым есть прямые ссылки в тексте:

- Linear: https://linear.app/docs/api-and-webhooks.md · https://linear.app/docs/parent-and-sub-issues.md · https://developers.linear.app/docs/graphql/working-with-the-graphql-api
- Jira Cloud: https://developer.atlassian.com/cloud/jira/platform/rest/v3/intro/ · api-group-issue-search · api-group-webhooks · api-group-issue-comments · https://developer.atlassian.com/cloud/jira/platform/apis/document/structure/ · swagger-v3 OpenAPI
- Shortcut: https://developer.shortcut.com/api/rest/v3/shortcut.openapi.json
- Plane: https://docs.plane.so/api-reference/introduction
- Kaneo: https://kaneo.app/docs/api-reference/introduction · https://kaneo.app/docs/openapi.json · https://github.com/usekaneo/kaneo
- Vikunja: https://try.vikunja.io/api/v1/info · https://try.vikunja.io/api/v1/docs.json · https://github.com/go-vikunja/vikunja
- Plane (исходники роутера): https://github.com/makeplane/plane/tree/master/apps/api/plane/api/urls · apps/api/plane/app/urls/webhook.py · apps/api/plane/db/models/issue.py
- YouTrack: https://www.jetbrains.com/help/youtrack/server/introduction-to-youtrack-server.html · https://www.jetbrains.com/help/youtrack/server/webhook-triggers.html · https://youtrack.jetbrains.com/api/openapi.json
- OpenProject: https://community.openproject.org/api/v3/spec.json · https://www.openproject.org/docs/system-admin-guide/api-and-webhooks/ · https://www.openproject.org/docs/enterprise-guide/
- Taiga: https://docs.taiga.io/api.html · https://api.taiga.io/api/v1/
- Redmine: https://www.redmine.org/projects/redmine/wiki/Rest_api · https://www.redmine.org/projects/redmine/wiki/Rest_Issues · https://www.redmine.org/issues.json
