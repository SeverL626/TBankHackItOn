# Meventus

Telegram-бот для создания и удобной организации мероприятий.

## Стек

- Kotlin 2.0 / JVM 17
- Gradle (Kotlin DSL)
- kotlin-telegram-bot — DSL для Bot API
- Exposed + PostgreSQL + HikariCP — хранилище
- Coroutines — фоновые задачи и шедулер
- HOCON (Typesafe Config) — конфигурация
- SLF4J + Logback — логирование

## Архитектура

```
src/main/kotlin/com/meventus
├── Application.kt              точка входа
├── config/                     загрузка и типы конфигурации
├── bot/                        слой Telegram
│   ├── MeventusBot.kt          сборка диспетчера
│   ├── commands/               /start, /new, /events, /my, /help
│   ├── callbacks/              обработчики inline-кнопок
│   ├── keyboards/              сборка клавиатур
│   ├── states/                 FSM пользователя
│   └── messages/               строковые ресурсы
├── domain/                     бизнес-логика, не зависит от Telegram и БД
│   ├── model/                  сущности (Event, User, Participant)
│   ├── repository/             интерфейсы репозиториев
│   └── service/                use-case-ы
├── infrastructure/             реализации внешних зависимостей
│   ├── persistence/            DatabaseFactory, таблицы Exposed, реализации репозиториев
│   └── scheduler/              напоминания и фоновые задачи
└── util/                       мелкие хелперы
```

Зависимости направлены внутрь: `bot` и `infrastructure` зависят от `domain`, но не наоборот.
Это позволяет:

- заменить kotlin-telegram-bot на pengrad или вебхуки без переписывания сервисов;
- подменить PostgreSQL на in-memory хранилище в тестах, реализовав интерфейсы из `domain/repository`;
- безболезненно вынести `infrastructure` или `bot` в отдельный Gradle-модуль, когда вырастем.

## Запуск

1. Создайте бота через `@BotFather` и получите токен.
2. Поднимите PostgreSQL.
3. Задайте переменные окружения:
   ```
   BOT_TOKEN=...
   BOT_USERNAME=...
   DATABASE_URL=jdbc:postgresql://localhost:5432/meventus
   DATABASE_USER=meventus
   DATABASE_PASSWORD=meventus
   ```
4. `./gradlew run`

## Команды бота

| Команда   | Что делает                          |
|-----------|-------------------------------------|
| `/start`  | регистрация пользователя            |
| `/new`    | создание мероприятия (FSM по шагам) |
| `/events` | афиша предстоящих мероприятий       |
| `/my`     | мои мероприятия (создал / иду)      |
| `/help`   | справка                             |

## Как расширять

- Новая команда → `bot/commands/MyCommand.kt`, регистрация в `MeventusBot`.
- Новая сущность → модель в `domain/model`, интерфейс репозитория в `domain/repository`,
  реализация Exposed в `infrastructure/persistence`, сервис в `domain/service`.
- Новый канал уведомлений → реализация `NotificationService` в `infrastructure`.
