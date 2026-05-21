# Meventus

Telegram-бот для создания и организации мероприятий с Telegram Mini App для статистики.

---

## Содержание

- [Стек](#стек)
- [Быстрый старт](#быстрый-старт)
- [Структура проекта](#структура-проекта)
- [База данных](#база-данных)
- [Команды бота](#команды-бота)
- [Машина состояний (FSM)](#машина-состояний-fsm)
- [Inline-callbacks (кнопки)](#inline-callbacks-кнопки)
- [Мини-приложение (WebApp)](#мини-приложение-webapp)
- [Конфигурация](#конфигурация)
- [Деплой](#деплой)
- [Как расширять](#как-расширять)

---

## Стек

| Технология | Назначение |
|---|---|
| Kotlin 2.0 / JVM 17 | язык и рантайм |
| Gradle (Kotlin DSL) | сборка |
| kotlin-telegram-bot 6.2.0 | DSL для Telegram Bot API |
| Exposed 0.55 + HikariCP + PostgreSQL | ORM и пул соединений |
| Typesafe Config (HOCON) | конфигурация через `application.conf` |
| SLF4J + Logback | логирование |
| `com.sun.net.httpserver` (JDK built-in) | HTTP-сервер для Mini App |

---

## Быстрый старт

### Переменные окружения

```bash
BOT_TOKEN=7415089385:AAE...          # токен от @BotFather
BOT_USERNAME=PhisMathBot             # username без @
DATABASE_URL=jdbc:postgresql://localhost:5432/meventus
DATABASE_USER=meventus
DATABASE_PASSWORD=meventus
WEBAPP_URL=https://your-domain.com   # HTTPS-адрес для Mini App
WEBAPP_PORT=8080                     # порт HTTP-сервера (по умолчанию 8080)
```

### Запуск локально

```bash
# 1. Поднять PostgreSQL
docker run -d --name pg \
  -e POSTGRES_DB=meventus \
  -e POSTGRES_USER=meventus \
  -e POSTGRES_PASSWORD=meventus \
  -p 5432:5432 postgres:16

# 2. Запустить бота
BOT_TOKEN=... BOT_USERNAME=... ./gradlew run
```

### Запуск через Docker

```bash
# Создать .env файл с переменными выше, затем:
docker compose up -d --build
```

---

## Структура проекта

```
src/main/kotlin/com/meventus/
│
├── Application.kt                    ← точка входа: запускает WebAppServer и бота
│
├── config/
│   ├── AppConfig.kt                  ← загружает все настройки из application.conf
│   ├── BotConfig.kt                  ← token, username
│   ├── DatabaseConfig.kt             ← jdbcUrl, username, password, maxPoolSize
│   └── WebAppConfig.kt               ← port, url для Mini App
│
├── bot/
│   ├── MeventusBot.kt                ← регистрирует ВСЕ handlers и commands
│   │
│   ├── commands/                     ← каждый файл = одна /команда
│   │   ├── Command.kt                ← интерфейс: name + register(dispatcher)
│   │   ├── StartCommand.kt           ← /start
│   │   ├── HelpCommand.kt            ← /help
│   │   ├── CreateEventCommand.kt     ← /new  (запускает FSM)
│   │   ├── ListEventsCommand.kt      ← /events (фильтр по тегам)
│   │   ├── MyEventsCommand.kt        ← /my
│   │   └── StatsCommand.kt           ← /stats (счётчик + кнопка Mini App)
│   │
│   ├── callbacks/                    ← обработчики нажатий inline-кнопок
│   │   ├── CallbackHandler.kt        ← интерфейс: prefix + register(dispatcher)
│   │   ├── JoinEventCallback.kt      ← prefix "ejoin:" — вступить в событие
│   │   ├── LeaveEventCallback.kt     ← prefix "leave:" — покинуть событие
│   │   └── EventDetailCallback.kt    ← prefix "edetail:" — полная карточка события
│   │
│   ├── keyboards/
│   │   ├── MainMenuKeyboard.kt       ← нижняя ReplyKeyboard с основными кнопками
│   │   └── EventKeyboard.kt          ← InlineKeyboard для карточки события
│   │
│   ├── states/
│   │   ├── StateStorage.kt           ← интерфейс + InMemoryStateStorage (HashMap)
│   │   └── UserState.kt              ← sealed interface со всеми состояниями FSM
│   │
│   ├── stats/
│   │   └── StatsStorage.kt           ← ConcurrentHashMap: кол-во сообщений + частота слов
│   │
│   └── messages/
│       └── Messages.kt               ← строковые константы (тексты ответов бота)
│
├── domain/                           ← бизнес-логика, НЕ зависит от Telegram и БД
│   ├── model/
│   │   ├── User.kt                   ← data class: telegramId, username, firstName
│   │   ├── Event.kt                  ← data class: см. раздел "База данных"
│   │   ├── EventStatus.kt            ← enum: DRAFT, PUBLISHED, CANCELLED, FINISHED
│   │   ├── EventTag.kt               ← enum: IT, SPORT, OUTDOORS, INDOORS
│   │   └── Participant.kt            ← data class: eventId, userId, joinedAt, contributed
│   │
│   ├── repository/                   ← только интерфейсы, без SQL
│   │   ├── UserRepository.kt
│   │   ├── EventRepository.kt
│   │   └── ParticipantRepository.kt
│   │
│   └── service/                      ← use-case: оркестрируют репозитории
│       ├── UserService.kt            ← registerIfAbsent
│       ├── EventService.kt           ← create, listUpcoming, listByTags, listByOwner
│       ├── ParticipantService.kt     ← join, leave, listByEvent, updateContribution
│       └── NotificationService.kt   ← отправка уведомлений участникам
│
├── infrastructure/
│   ├── persistence/
│   │   ├── DatabaseFactory.kt        ← HikariCP + SchemaUtils.create(все таблицы)
│   │   ├── tables/
│   │   │   ├── UsersTable.kt
│   │   │   ├── EventsTable.kt
│   │   │   ├── EventTagsTable.kt     ← связь event ↔ теги (many-to-many)
│   │   │   └── ParticipantsTable.kt
│   │   └── repository/
│   │       ├── UserRepositoryImpl.kt
│   │       ├── EventRepositoryImpl.kt
│   │       └── ParticipantRepositoryImpl.kt
│   └── scheduler/
│       └── ReminderScheduler.kt      ← фоновые напоминания участникам
│
├── webapp/
│   └── WebAppServer.kt               ← JDK HttpServer на WEBAPP_PORT
│       ├── GET /                     → отдаёт webapp/index.html
│       └── GET /api/stats?userId=X  → JSON {messageCount, topWord}
│
└── util/
    ├── DateUtils.kt                  ← parse/format дат (формат "dd.MM.yyyy HH:mm", МСК)
    └── extensions/
        └── StringExtensions.kt
```

---

## База данных

Таблицы создаются автоматически при старте через `SchemaUtils.create()`.

### users
| Колонка | Тип | Описание |
|---|---|---|
| `telegram_id` | BIGINT PK | ID пользователя в Telegram |
| `username` | VARCHAR(64) NULL | @username |
| `first_name` | VARCHAR(128) | имя |
| `created_at` | TIMESTAMP | дата регистрации |

### events
| Колонка | Тип | Описание |
|---|---|---|
| `id` | BIGSERIAL PK | — |
| `owner_id` | BIGINT FK→users | организатор |
| `title` | VARCHAR(256) | название |
| `short_description` | VARCHAR(512) | краткое описание (в списке) |
| `description` | TEXT | полное описание |
| `photo_file_id` | VARCHAR(256) NULL | Telegram file_id фотографии |
| `address` | VARCHAR(512) | адрес проведения |
| `starts_at` | TIMESTAMP | дата и время |
| `cost` | BIGINT | стоимость в рублях (0 = бесплатно) |
| `status` | ENUM | DRAFT / PUBLISHED / CANCELLED / FINISHED |
| `created_at` | TIMESTAMP | — |

### event_tags
| Колонка | Тип | Описание |
|---|---|---|
| `event_id` | BIGINT FK→events PK | — |
| `tag` | ENUM PK | IT / SPORT / OUTDOORS / INDOORS |

> Одно событие может иметь несколько тегов. Связь many-to-many через эту таблицу.

### participants
| Колонка | Тип | Описание |
|---|---|---|
| `event_id` | BIGINT FK→events PK | — |
| `user_id` | BIGINT FK→users PK | — |
| `joined_at` | TIMESTAMP | когда вступил |
| `contributed` | BIGINT | сколько скинул рублей (0 по умолчанию) |

### Схема связей

```
users ──< events (owner_id)
users ──< participants (user_id)
events ──< participants (event_id)
events ──< event_tags (event_id)
```

---

## Команды бота

| Команда | Файл | Что делает |
|---|---|---|
| `/start` | `StartCommand.kt` | регистрирует пользователя в БД, показывает меню |
| `/new` | `CreateEventCommand.kt` | запускает FSM создания события |
| `/events` | `ListEventsCommand.kt` | показывает фильтр тегов, затем список событий |
| `/my` | `MyEventsCommand.kt` | мои события (создал / участвую) |
| `/stats` | `StatsCommand.kt` | кол-во сообщений, топ-слово, кнопка Mini App |
| `/help` | `HelpCommand.kt` | список команд |

---

## Машина состояний (FSM)

**Файлы:** `bot/states/UserState.kt`, `bot/states/StateStorage.kt`

Каждый пользователь в каждый момент находится в одном состоянии. По умолчанию — `Idle`.

```
UserState (sealed interface)
│
├── Idle                          ← обычный режим, нет активного диалога
│
└── Состояния создания события (/new):
    │
    ├── AwaitingEventTitle
    │     └── → AwaitingEventShortDesc(title)
    │
    ├── AwaitingEventShortDesc(title)
    │     └── → AwaitingEventDescription(title, shortDesc)
    │
    ├── AwaitingEventDescription(title, shortDesc)
    │     └── → AwaitingEventAddress(title, shortDesc, desc)
    │
    ├── AwaitingEventAddress(title, shortDesc, desc)
    │     └── → AwaitingEventDate(title, shortDesc, desc, address)
    │
    ├── AwaitingEventDate(title, shortDesc, desc, address)
    │     └── → AwaitingEventCost(title, shortDesc, desc, address, startsAt)
    │
    ├── AwaitingEventCost(title, shortDesc, desc, address, startsAt)
    │     └── → AwaitingEventPhoto(title, shortDesc, desc, address, startsAt, cost)
    │
    ├── AwaitingEventPhoto(title, shortDesc, desc, address, startsAt, cost)
    │     ├── пользователь прислал фото → photoFileId сохраняется
    │     ├── пользователь написал /skip → photoFileId = null
    │     └── → AwaitingEventTags(...все данные..., photoFileId, selectedTags=∅)
    │
    └── AwaitingEventTags(...все данные..., selectedTags: Set<EventTag>)
          ├── пользователь кликает тег → selectedTags обновляется, клавиатура перерисовывается
          └── пользователь кликает "Готово" → событие сохраняется, state → Idle
```

**Как работает:**

```
Пользователь пишет текст
        ↓
text { } в MeventusBot.kt
        ↓
EventCreateHandler проверяет stateStorage.get(userId)
        ↓
  Idle?  → только записать в StatsStorage, игнорировать
  Awaiting*? → обработать шаг, перейти к следующему состоянию
```

**Хранение состояния:** в памяти (`ConcurrentHashMap`). При перезапуске бота незавершённые диалоги сбрасываются.

---

## Inline-callbacks (кнопки)

Формат callback data: `prefix:данные`

| Prefix | Файл | Действие |
|---|---|---|
| `edetail:` | `EventDetailCallback.kt` | показать полную карточку события. Данные: `eventId` |
| `ejoin:` | `JoinEventCallback.kt` | вступить в событие. Данные: `eventId` |
| `leave:` | `LeaveEventCallback.kt` | покинуть событие. Данные: `eventId` |
| `etag:` | внутри FSM тегов | переключить тег при создании. Данные: `TAG:BITMASK` |
| `etag_done:` | внутри FSM тегов | подтвердить теги. Данные: `BITMASK` |
| `filter:` | `ListEventsCommand` / callback | переключить тег в фильтре поиска. Данные: `TAG:BITMASK` |
| `filter_search:` | `ListEventsCommand` / callback | найти события. Данные: `BITMASK` |

**BITMASK** — число 0–15, где каждый бит = тег:
```
bit 0 (1)  = IT
bit 1 (2)  = SPORT
bit 2 (4)  = OUTDOORS
bit 3 (8)  = INDOORS

Пример: IT + SPORT = 1 + 2 = 3
```

---

## Мини-приложение (WebApp)

**Файлы:** `webapp/WebAppServer.kt`, `resources/webapp/index.html`

Запускается на `WEBAPP_PORT` (по умолчанию 8080) при старте приложения.

| Endpoint | Описание |
|---|---|
| `GET /` | HTML страница Mini App |
| `GET /api/stats?userId=123` | JSON: `{"messageCount": 42, "topWord": "привет"}` |

**Как открывается:** команда `/stats` отправляет сообщение с кнопкой типа `web_app`. Кнопка появляется только если `WEBAPP_URL` начинается с `https://`.

**Данные пользователя:** страница читает `Telegram.WebApp.initDataUnsafe.user.id` и запрашивает `/api/stats?userId=<id>`.

**Изменить вёрстку:** `src/main/resources/webapp/index.html` — обычный HTML/CSS/JS.

---

## Конфигурация

**Файл:** `src/main/resources/application.conf`

```hocon
bot {
    token    = ${?BOT_TOKEN}
    username = ${?BOT_USERNAME}
}

webapp {
    port = 8080
    port = ${?WEBAPP_PORT}
    url  = "http://localhost:8080"
    url  = ${?WEBAPP_URL}
}

database {
    jdbcUrl    = "jdbc:postgresql://localhost:5432/meventus"
    jdbcUrl    = ${?DATABASE_URL}
    username   = "meventus"
    username   = ${?DATABASE_USER}
    password   = "meventus"
    password   = ${?DATABASE_PASSWORD}
    maxPoolSize = 10
}
```

Значения из `${?VAR}` переопределяют дефолт если переменная окружения задана.

---

## Деплой

Деплой настроен через GitHub Actions (`.github/workflows/deploy.yml`).

**Триггер:** push или merge в ветку `main`.

**Процесс:**
1. GitHub Actions подключается по SSH к VPS
2. На VPS в `/opt/myapp` делает `git pull origin main`
3. Запускает `docker compose up -d --build`

**Что нужно на VPS:**

```bash
# 1. Создать папку и склонировать репо
mkdir -p /opt/myapp
cd /opt/myapp
git clone https://github.com/SeverL626/TBankHackItOn.git .

# 2. Создать .env файл (НЕ коммитить в git!)
cat > /opt/myapp/.env << EOF
BOT_TOKEN=...
BOT_USERNAME=...
DATABASE_URL=jdbc:postgresql://localhost:5432/meventus
DATABASE_USER=meventus
DATABASE_PASSWORD=meventus
WEBAPP_URL=https://your-domain.com
EOF

# 3. Добавить GitHub secrets в настройках репо:
#    VPS_HOST, VPS_USER, VPS_SSH_KEY, VPS_SSH_PORT
```

**Секреты GitHub Actions:**

| Secret | Значение |
|---|---|
| `VPS_HOST` | IP или домен сервера |
| `VPS_USER` | пользователь SSH |
| `VPS_SSH_KEY` | приватный SSH ключ |
| `VPS_SSH_PORT` | порт SSH (обычно 22) |

---

## Как расширять

### Добавить новую команду

1. Создать `bot/commands/MyCommand.kt`:
```kotlin
class MyCommand : Command {
    override val name = "mycommand"
    override fun register(dispatcher: Dispatcher) {
        dispatcher.command(name) { /* ... */ }
    }
}
```
2. Зарегистрировать в `MeventusBot.kt`: `MyCommand().register(this)`

### Добавить новый inline-callback

1. Создать `bot/callbacks/MyCallback.kt`, реализовать `CallbackHandler`
2. Придумать уникальный prefix (например `"myaction:"`)
3. Зарегистрировать в `MeventusBot.kt`

### Добавить новую сущность в БД

1. Модель → `domain/model/MyEntity.kt`
2. Интерфейс → `domain/repository/MyRepository.kt`
3. Таблица Exposed → `infrastructure/persistence/tables/MyTable.kt`
4. Реализация → `infrastructure/persistence/repository/MyRepositoryImpl.kt`
5. Сервис → `domain/service/MyService.kt`
6. Добавить таблицу в `DatabaseFactory.kt`: `SchemaUtils.create(..., MyTable)`

### Добавить шаг в FSM создания события

1. Добавить новый `data class AwaitingMyStep(...)` в `UserState.kt`
2. В `EventCreateHandler.kt` добавить ветку `is UserState.AwaitingMyStep → ...`

### Изменить тексты бота

Все строки в `bot/messages/Messages.kt`.

### Изменить мини-приложение

Только `src/main/resources/webapp/index.html`. После изменения — перезапуск (`./gradlew run`).
