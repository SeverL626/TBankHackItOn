# Meventus

Telegram-бот для создания и организации мероприятий. Участники записываются через бота, организаторы управляют событиями через встроенное Mini App с Y2K-дизайном.

---

## Содержание

- [Стек](#стек)
- [Быстрый старт](#быстрый-старт)
- [Архитектура](#архитектура)
- [Структура проекта](#структура-проекта)
- [База данных](#база-данных)
- [Команды бота](#команды-бота)
- [Постоянная клавиатура](#постоянная-клавиатура)
- [Машина состояний (FSM)](#машина-состояний-fsm)
- [Inline-callbacks](#inline-callbacks)
- [Mini App (WebApp)](#mini-app-webapp)
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
| Exposed 0.55 + HikariCP | ORM и пул соединений к PostgreSQL |
| PostgreSQL 16 | база данных |
| Typesafe Config (HOCON) | конфигурация через `application.conf` + переменные окружения |
| SLF4J + Logback | логирование |
| `com.sun.net.httpserver` (JDK) | встроенный HTTP-сервер для Mini App (без зависимостей) |
| Telegram WebApp JS SDK | инициализация Mini App в WebView |
| Docker + Docker Compose | контейнеризация |
| GitHub Actions | CI/CD деплой на VPS по SSH |
| Cloudflare Tunnel | бесплатный HTTPS-туннель для Mini App при локальной разработке |

---

## Быстрый старт

### Переменные окружения

```bash
BOT_TOKEN=7415089385:AAE...          # токен от @BotFather
BOT_USERNAME=PhisMathBot             # username без @
DATABASE_URL=jdbc:postgresql://localhost:5432/meventus
DATABASE_USER=meventus
DATABASE_PASSWORD=meventus
WEBAPP_URL=https://your-domain.com   # HTTPS-адрес Mini App (http не работает в Telegram)
WEBAPP_PORT=8080                     # порт HTTP-сервера (по умолчанию 8080)
```

### Локальный запуск

```bash
# 1. PostgreSQL
docker run -d --name pg \
  -e POSTGRES_DB=meventus \
  -e POSTGRES_USER=meventus \
  -e POSTGRES_PASSWORD=meventus \
  -p 5432:5432 postgres:16

# 2. HTTPS-туннель для Mini App (в отдельном терминале)
cloudflared tunnel --url http://localhost:8080
# → скопировать URL вида https://random.trycloudflare.com

# 3. Запуск бота
export WEBAPP_URL="https://random.trycloudflare.com"
export BOT_TOKEN="..."
./gradlew run
```

### Запуск через Docker Compose

```bash
# Создать .env с переменными выше, затем:
docker compose up -d --build
```

> Таблицы БД создаются автоматически при старте через `SchemaUtils.createMissingTablesAndColumns()` — безопасно для существующих данных.

---

## Архитектура

```
┌──────────────────────────────────────────────────────────────────┐
│                         Telegram API                             │
└───────────────────┬──────────────────────────┬───────────────────┘
                    │ Bot polling               │ Mini App WebView
                    ▼                           ▼
         ┌─────────────────┐         ┌─────────────────────┐
         │  MeventusBot    │         │   WebAppServer      │
         │  (bot DSL)      │         │   (JDK HttpServer)  │
         │                 │         │                     │
         │ commands/       │         │ GET  /              │
         │ callbacks/      │         │ GET  /api/events    │
         │ handlers/       │         │ GET  /api/event     │
         │ keyboards/      │         │ POST /api/join      │
         │ states/         │         │ POST /api/leave     │
         │ stats/          │         │ POST /api/event/update│
         └────────┬────────┘         │ GET  /api/admin     │
                  │                  │ GET  /api/stats     │
                  │                  └──────────┬──────────┘
                  │                             │
                  └──────────┬──────────────────┘
                             ▼
              ┌──────────────────────────────┐
              │         domain/              │
              │  EventService                │
              │  ParticipantService          │
              │  UserService                 │
              └──────────────┬───────────────┘
                             │
              ┌──────────────▼───────────────┐
              │      infrastructure/          │
              │  EventRepositoryImpl         │
              │  ParticipantRepositoryImpl   │
              │  UserRepositoryImpl          │
              │  DatabaseFactory (HikariCP)  │
              └──────────────┬───────────────┘
                             │
              ┌──────────────▼───────────────┐
              │         PostgreSQL            │
              │  users / events / event_tags  │
              │  participants                 │
              └──────────────────────────────┘
```

**Принцип слоёв:**
- `domain/` — чистая бизнес-логика. Не импортирует ни Telegram, ни Exposed.
- `bot/` и `webapp/` — точки входа. Вызывают только сервисы из `domain/`.
- `infrastructure/` — реализации репозиториев с SQL через Exposed.

**Разделение сервисов:** `Application.kt` создаёт сервисы один раз и передаёт их и в `WebAppServer`, и в `MeventusBot` — оба слоя работают с одними объектами.

---

## Структура проекта

```
src/main/kotlin/com/meventus/
│
├── Application.kt                     ← точка входа: init DB → WebAppServer → Bot
│
├── config/
│   ├── AppConfig.kt                   ← собирает все конфиги из application.conf
│   ├── BotConfig.kt                   ← token, username
│   ├── DatabaseConfig.kt              ← jdbcUrl, user, password, maxPoolSize
│   └── WebAppConfig.kt                ← port, url
│
├── bot/
│   ├── MeventusBot.kt                 ← регистрирует все handlers/commands/callbacks,
│   │                                     вызывает bot.setMyCommands(...)
│   ├── commands/
│   │   ├── Command.kt                 ← interface: name + register(Dispatcher)
│   │   ├── StartCommand.kt            ← /start — регистрация + постоянная клавиатура
│   │   ├── HelpCommand.kt             ← /help
│   │   ├── CreateEventCommand.kt      ← /new — запускает FSM создания
│   │   ├── ListEventsCommand.kt       ← /events — фильтр тегов → список
│   │   ├── MyEventsCommand.kt         ← /my — создал + участвую
│   │   ├── BroadcastCommand.kt        ← /broadcast — рассылка участникам
│   │   └── StatsCommand.kt            ← /stats — кнопка открытия Mini App
│   │
│   ├── callbacks/
│   │   ├── CallbackHandler.kt         ← interface: prefix + register(Dispatcher)
│   │   ├── EventDetailCallback.kt     ← "edetail:ID" — полная карточка события
│   │   ├── JoinEventCallback.kt       ← "ejoin:ID"  — вступить (блокирует организатора)
│   │   └── LeaveEventCallback.kt      ← "leave:ID"  — покинуть
│   │
│   ├── handlers/
│   │   ├── EventCreateHandler.kt      ← FSM создания: text + photo + ctag: callbacks
│   │   ├── BroadcastHandler.kt        ← FSM рассылки: bcast: callback + text
│   │   └── MenuKeyboardHandler.kt     ← перехват нажатий кнопок постоянной клавиатуры
│   │
│   ├── keyboards/
│   │   ├── TagKeyboard.kt             ← InlineKeyboard тегов (создание + фильтр)
│   │   ├── EventKeyboard.kt           ← InlineKeyboard карточки события
│   │   └── MainMenuKeyboard.kt        ← ReplyKeyboard (нижние кнопки)
│   │
│   ├── states/
│   │   ├── UserState.kt               ← sealed interface со всеми состояниями FSM
│   │   └── StateStorage.kt            ← interface + InMemoryStateStorage (HashMap)
│   │
│   ├── stats/
│   │   └── StatsStorage.kt            ← ConcurrentHashMap: счётчик сообщений + топ-слово
│   │
│   └── messages/
│       └── Messages.kt                ← строковые константы (тексты бота)
│
├── domain/
│   ├── model/
│   │   ├── User.kt                    ← data class: telegramId, username, firstName, createdAt
│   │   ├── Event.kt                   ← data class: все поля события
│   │   ├── EventStatus.kt             ← enum: DRAFT, PUBLISHED, CANCELLED, FINISHED
│   │   ├── EventTag.kt                ← enum: IT(bit=1), SPORT(bit=2), OUTDOORS(bit=4), INDOORS(bit=8)
│   │   └── Participant.kt             ← data class: eventId, userId, joinedAt, contributed
│   │
│   ├── repository/                    ← только интерфейсы, без SQL
│   │   ├── UserRepository.kt
│   │   ├── EventRepository.kt
│   │   └── ParticipantRepository.kt
│   │
│   └── service/
│       ├── UserService.kt             ← registerIfAbsent
│       ├── EventService.kt            ← create, findById, listUpcoming, listByTags,
│       │                                 listByOwner, update, cancel
│       └── ParticipantService.kt      ← join, leave, isParticipant, listByEvent,
│                                         listEventsByUser, updateContribution
│
├── infrastructure/
│   ├── persistence/
│   │   ├── DatabaseFactory.kt         ← HikariCP pool + createMissingTablesAndColumns
│   │   ├── tables/
│   │   │   ├── UsersTable.kt
│   │   │   ├── EventsTable.kt
│   │   │   ├── EventTagsTable.kt      ← PK(eventId, tag) — many-to-many
│   │   │   └── ParticipantsTable.kt   ← PK(eventId, userId)
│   │   └── repository/
│   │       ├── UserRepositoryImpl.kt
│   │       ├── EventRepositoryImpl.kt      ← save() — insert если id==0, update иначе
│   │       └── ParticipantRepositoryImpl.kt ← findEventsByUser: JOIN + batch тегов (2 запроса)
│   └── scheduler/
│       └── ReminderScheduler.kt       ← фоновые напоминания (заглушка)
│
├── webapp/
│   └── WebAppServer.kt                ← JDK HttpServer, все REST-эндпоинты
│
└── util/
    ├── DateUtils.kt                   ← parse/format дат ("dd.MM.yyyy HH:mm", UTC)
    └── extensions/
        └── StringExtensions.kt
```

---

## База данных

Таблицы создаются автоматически при старте. Существующие данные не затрагиваются.

### users
| Колонка | Тип | Описание |
|---|---|---|
| `telegram_id` | BIGINT PK | ID пользователя в Telegram |
| `username` | VARCHAR(64) NULL | @username |
| `first_name` | VARCHAR(128) | имя |
| `created_at` | TIMESTAMP | дата первого /start |

### events
| Колонка | Тип | Описание |
|---|---|---|
| `id` | BIGSERIAL PK | — |
| `owner_id` | BIGINT | telegram_id организатора |
| `title` | VARCHAR(256) | название |
| `short_description` | VARCHAR(512) | краткое описание (в карточке списка) |
| `description` | TEXT | полное описание |
| `photo_file_id` | VARCHAR(256) NULL | Telegram file_id фото |
| `address` | VARCHAR(512) | адрес |
| `starts_at` | TIMESTAMP | дата и время начала |
| `cost` | BIGINT | стоимость в рублях (0 = бесплатно) |
| `status` | ENUM | DRAFT / PUBLISHED / CANCELLED / FINISHED |
| `created_at` | TIMESTAMP | время создания |

### event_tags
| Колонка | Тип | Описание |
|---|---|---|
| `event_id` | BIGINT FK→events PK | — |
| `tag` | ENUM PK | IT / SPORT / OUTDOORS / INDOORS |

Одно событие может иметь несколько тегов. Хранятся отдельными строками.

### participants
| Колонка | Тип | Описание |
|---|---|---|
| `event_id` | BIGINT FK→events PK | — |
| `user_id` | BIGINT FK→users PK | — |
| `joined_at` | TIMESTAMP | момент записи |
| `contributed` | BIGINT | взнос в рублях (по умолчанию 0) |

### Связи

```
users ──< events        (events.owner_id → users.telegram_id)
events ──< event_tags   (event_tags.event_id → events.id)
users ──< participants  (participants.user_id → users.telegram_id)
events ──< participants (participants.event_id → events.id)
```

### Производительность запросов

`ParticipantRepositoryImpl.findEventsByUser` использует **2 запроса** вместо N+1:
1. `JOIN participants + events` — получить все события пользователя
2. Один `SELECT` по всем `event_id` из шага 1 — загрузить теги пакетом

`isParticipant` использует `.limit(1)` — читает максимум одну строку по PRIMARY KEY.

---

## Команды бота

| Команда | Файл | Что делает |
|---|---|---|
| `/start` | `StartCommand.kt` | регистрирует пользователя, показывает постоянную клавиатуру |
| `/new` | `CreateEventCommand.kt` | запускает 8-шаговый FSM создания мероприятия |
| `/events` | `ListEventsCommand.kt` | фильтр по тегам через inline-кнопки, затем список |
| `/my` | `MyEventsCommand.kt` | два раздела: "Организую" и "Участвую" |
| `/broadcast` | `BroadcastCommand.kt` | выбор мероприятия для рассылки участникам |
| `/stats` | `StatsCommand.kt` | кнопка открытия Mini App (только при HTTPS URL) |
| `/help` | `HelpCommand.kt` | список команд |

---

## Постоянная клавиатура

После `/start` внизу экрана появляется клавиатура с кнопками. Нажатие на кнопку отправляет текст, который перехватывает `MenuKeyboardHandler`:

| Кнопка | Действие |
|---|---|
| 📋 Мероприятия | список ближайших событий (до 10) |
| ⭐ Мои события | то же, что `/my` |
| ➕ Создать событие | запускает FSM создания |
| 📢 Рассылка | то же, что `/broadcast` |
| 📊 Статистика | кнопка открытия Mini App |

Клавиатура работает только в состоянии `Idle`. Если пользователь в середине FSM — кнопки игнорируются (чтобы не сломать диалог).

---

## Машина состояний (FSM)

**Файлы:** `bot/states/UserState.kt`, `bot/handlers/EventCreateHandler.kt`, `bot/handlers/BroadcastHandler.kt`

Каждый пользователь находится ровно в одном состоянии. По умолчанию — `Idle`. Состояние хранится в памяти (`ConcurrentHashMap`) — сбрасывается при рестарте бота.

```
UserState (sealed interface)
│
├── Idle
│     ← обычный режим, кнопки клавиатуры активны
│
├── ── FSM создания мероприятия (/new) ──────────────────────────────
│
├── AwaitingEventTitle
│     пользователь вводит название
│     └──→ AwaitingEventShortDesc(title)
│
├── AwaitingEventShortDesc(title)
│     пользователь вводит краткое описание (1–2 строки, для карточки)
│     └──→ AwaitingEventDescription(title, shortDesc)
│
├── AwaitingEventDescription(title, shortDesc)
│     пользователь вводит полное описание
│     └──→ AwaitingEventAddress(title, shortDesc, description)
│
├── AwaitingEventAddress(title, shortDesc, description)
│     пользователь вводит адрес
│     └──→ AwaitingEventDate(title, shortDesc, description, address)
│
├── AwaitingEventDate(title, shortDesc, description, address)
│     пользователь вводит дату в формате "ДД.ММ.ГГГГ ЧЧ:ММ"
│     при ошибке формата — повторный запрос
│     └──→ AwaitingEventCost(title, shortDesc, description, address, startsAt)
│
├── AwaitingEventCost(title, shortDesc, description, address, startsAt)
│     пользователь вводит стоимость (целое ≥ 0, 0 = бесплатно)
│     └──→ AwaitingEventPhoto(title, shortDesc, description, address, startsAt, cost)
│
├── AwaitingEventPhoto(title, shortDesc, description, address, startsAt, cost)
│     пользователь отправляет фото или пишет "пропустить"
│     └──→ AwaitingEventTags(...все данные..., photoFileId?, selectedTags=∅)
│
├── AwaitingEventTags(...все данные..., selectedTags: Set<EventTag>)
│     inline-кнопки тегов (callback "ctag:BITMASK") переключают теги
│     кнопка "Готово" (callback "ctag_done:BITMASK") → сохраняет событие → Idle
│
└── ── FSM рассылки (/broadcast) ────────────────────────────────────
│
└── AwaitingBroadcast(eventId: Long)
      пользователь выбрал мероприятие (callback "bcast:ID")
      вводит текст сообщения
      бот отправляет его всем участникам через sendMessage(userId)
      └──→ Idle
```

**Поток обработки текста:**

```
Telegram → text { } в MeventusBot (записывает StatsStorage)
         → MenuKeyboardHandler.text { } (проверяет Idle + текст кнопки)
         → EventCreateHandler.text { } (when по состоянию)
         → BroadcastHandler.text { } (проверяет AwaitingBroadcast)
```

Все `text { }` хэндлеры регистрируются и вызываются последовательно. Каждый проверяет состояние сам и делает `return@text` если не его очередь.

---

## Inline-callbacks

Формат callback data: `prefix:данные`

| Prefix | Файл | Действие |
|---|---|---|
| `edetail:ID` | `EventDetailCallback.kt` | полная карточка события (текст или фото) |
| `ejoin:ID` | `JoinEventCallback.kt` | вступить; организатор заблокирован |
| `leave:ID` | `LeaveEventCallback.kt` | покинуть мероприятие |
| `ctag:BITMASK` | `EventCreateHandler.kt` | переключить тег при создании события |
| `ctag_done:BITMASK` | `EventCreateHandler.kt` | подтвердить теги → создать событие |
| `filter:BITMASK` | `ListEventsCommand.kt` | переключить тег в фильтре `/events` |
| `fsearch:BITMASK` | `ListEventsCommand.kt` | показать отфильтрованные события |
| `bcast:ID` | `BroadcastHandler.kt` | выбрать мероприятие для рассылки |

**Bitmask тегов** — число 0–15, кодирует любой набор тегов в одном числе:

```
IT       = 1  (бит 0)
SPORT    = 2  (бит 1)
OUTDOORS = 4  (бит 2)
INDOORS  = 8  (бит 3)

IT + OUTDOORS = 1 + 4 = 5
```

Это позволяет хранить весь текущий набор выбранных тегов прямо в callback data кнопки — без дополнительного хранилища состояния.

---

## Mini App (WebApp)

**Файлы:** `webapp/WebAppServer.kt`, `resources/webapp/index.html`

Запускается как отдельный HTTP-сервер на `WEBAPP_PORT` (по умолчанию 8080). Требует HTTPS-URL для открытия из Telegram — при локальной разработке используется Cloudflare Tunnel.

### REST API

| Метод | Путь | Параметры | Описание |
|---|---|---|---|
| GET | `/` | — | HTML страница Mini App |
| GET | `/api/events` | `userId`, `tags` (bitmask), `search` | список предстоящих событий с признаком участия |
| GET | `/api/event` | `id`, `userId` | полные данные события + статистика взносов |
| POST | `/api/join` | body: `eventId`, `userId` | записаться на мероприятие |
| POST | `/api/leave` | body: `eventId`, `userId` | отказаться от участия |
| POST | `/api/event/update` | body: все поля события | изменить мероприятие (только организатор) |
| GET | `/api/admin` | `userId` | события пользователя как организатора + статистика |
| GET | `/api/stats` | `userId` | счётчик сообщений и топ-слово |

Все endpoints возвращают JSON. CORS-заголовки добавлены для работы из WebView.

### Защита

- `/api/join` — блокирует организатора (возвращает `{"ok":false,"error":"owner cannot join own event"}`)
- `/api/event/update` — проверяет что `userId == event.ownerId`, иначе 403-подобный ответ

### Вкладки Mini App

| Вкладка | Содержимое |
|---|---|
| **EVENTS** | поиск по тексту + фильтр по тегам + карточки событий + кнопка "Участвовать / Покинуть" |
| **MINE** | события где пользователь участник или организатор |
| **ADMIN** | сводная статистика организатора + карточки с кнопками "Детали" и "Изменить" |

### Форма редактирования (ADMIN)

Клик на "✏ ИЗМЕНИТЬ" открывает overlay с предзаполненными полями:
- название, краткое / полное описание, адрес, дата/время, стоимость, теги
- `POST /api/event/update` — сохранение, после успеха панель обновляется

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
    jdbcUrl     = "jdbc:postgresql://localhost:5432/meventus"
    jdbcUrl     = ${?DATABASE_URL}
    username    = "meventus"
    username    = ${?DATABASE_USER}
    password    = "meventus"
    password    = ${?DATABASE_PASSWORD}
    maxPoolSize = 10
}
```

`${?VAR}` — значение из переменной окружения переопределяет дефолт только если она задана.

---

## Деплой

Настроен через GitHub Actions (`.github/workflows/deploy.yml`).

**Триггер:** push или merge в ветку `main`.

**Процесс на CI:**
1. Подключиться по SSH к VPS
2. `git pull origin main` в `/opt/myapp`
3. `docker compose up -d --build`

### Подготовка VPS

```bash
mkdir -p /opt/myapp
cd /opt/myapp
git clone https://github.com/SeverL626/TBankHackItOn.git .

# .env — НЕ коммитить в git
cat > /opt/myapp/.env << EOF
BOT_TOKEN=...
BOT_USERNAME=...
DATABASE_URL=jdbc:postgresql://localhost:5432/meventus
DATABASE_USER=meventus
DATABASE_PASSWORD=meventus
WEBAPP_URL=https://your-domain.com
EOF
```

### Secrets в GitHub

| Secret | Значение |
|---|---|
| `VPS_HOST` | IP или домен сервера |
| `VPS_USER` | пользователь SSH |
| `VPS_SSH_KEY` | приватный SSH-ключ |
| `VPS_SSH_PORT` | порт SSH (обычно 22) |

---

## Как расширять

### Добавить команду

```kotlin
// 1. bot/commands/MyCommand.kt
class MyCommand : Command {
    override val name = "mycmd"
    override fun register(dispatcher: Dispatcher) {
        dispatcher.command(name) { /* ... */ }
    }
}

// 2. MeventusBot.kt — добавить в dispatch { }
MyCommand().register(this)

// 3. MeventusBot.kt — добавить в bot.setMyCommands(...)
BotCommand("mycmd", "Описание команды"),
```

### Добавить inline-callback

```kotlin
// 1. bot/callbacks/MyCallback.kt
class MyCallback : CallbackHandler {
    override val prefix = "myaction:"
    override fun register(dispatcher: Dispatcher) {
        dispatcher.callbackQuery {
            val data = callbackQuery.data ?: return@callbackQuery
            if (!data.startsWith(prefix)) return@callbackQuery
            val id = data.removePrefix(prefix).toLongOrNull() ?: return@callbackQuery
            // ...
        }
    }
}

// 2. MeventusBot.kt
MyCallback().register(this)
```

### Добавить сущность в БД

1. `domain/model/MyEntity.kt` — data class
2. `domain/repository/MyRepository.kt` — интерфейс
3. `infrastructure/persistence/tables/MyTable.kt` — Exposed Table
4. `infrastructure/persistence/repository/MyRepositoryImpl.kt`
5. `domain/service/MyService.kt`
6. `DatabaseFactory.kt` — добавить в `SchemaUtils.createMissingTablesAndColumns(..., MyTable)`

### Добавить шаг в FSM

```kotlin
// UserState.kt
data class AwaitingMyStep(val prev: String) : UserState

// EventCreateHandler.kt — добавить ветку в when
is UserState.AwaitingMyStep -> {
    stateStorage.set(userId, UserState.NextStep(state.prev, text))
    bot.sendMessage(chatId, "Следующий шаг:")
}
```

### Добавить API-эндпоинт в Mini App

```kotlin
// WebAppServer.kt — добавить до server.executor = null
server.createContext("/api/myendpoint") { exchange ->
    val params = parseQuery(exchange.requestURI.query)
    // ...
    sendJson(exchange, """{"result": "..."}""")
}
```

### Изменить Mini App вёрстку

Только `src/main/resources/webapp/index.html`. Чистый HTML/CSS/JS, без сборщиков.
После изменения — перезапуск (`./gradlew run`).
