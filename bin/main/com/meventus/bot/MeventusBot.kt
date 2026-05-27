package com.meventus.bot

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.BotCommand
import com.meventus.bot.callbacks.EventDetailCallback
import com.meventus.bot.callbacks.JoinEventCallback
import com.meventus.bot.callbacks.LeaveEventCallback
import com.meventus.bot.commands.BroadcastCommand
import com.meventus.bot.commands.CancelCommand
import com.meventus.bot.commands.CreateEventCommand
import com.meventus.bot.commands.HelpCommand
import com.meventus.bot.commands.ListEventsCommand
import com.meventus.bot.commands.MyEventsCommand
import com.meventus.bot.commands.StartCommand
import com.meventus.bot.commands.StatsCommand
import com.meventus.bot.commands.TechUpdatesCommand
import com.meventus.bot.handlers.BroadcastHandler
import com.meventus.bot.handlers.EventCreateHandler
import com.meventus.bot.handlers.EventManageHandler
import com.meventus.bot.handlers.GroupLifecycleHandler
import com.meventus.bot.handlers.GroupEventHandler
import com.meventus.bot.handlers.MenuKeyboardHandler
import com.meventus.bot.handlers.PaymentHandler
import com.meventus.bot.notifications.EventReminderService
import com.meventus.bot.states.InMemoryStateStorage
import com.meventus.bot.stats.StatsStorage
import com.meventus.config.AppConfig
import com.meventus.domain.service.CustomReminderService
import com.meventus.domain.service.EventService
import com.meventus.domain.service.ParticipantService
import com.meventus.domain.service.TechUpdateService
import com.meventus.domain.service.UserService
import com.meventus.infrastructure.persistence.repository.CustomReminderRepositoryImpl
import com.meventus.infrastructure.persistence.repository.EventRepositoryImpl
import com.meventus.infrastructure.persistence.repository.ParticipantRepositoryImpl
import com.meventus.infrastructure.persistence.repository.TechUpdateRepositoryImpl
import com.meventus.infrastructure.persistence.repository.UserRepositoryImpl
import java.net.HttpURLConnection
import java.net.URL

class MeventusBot(private val config: AppConfig) {

    private val userService = UserService(UserRepositoryImpl())
    private val eventService = EventService(EventRepositoryImpl())
    private val participantService = ParticipantService(ParticipantRepositoryImpl())
    private val customReminderService = CustomReminderService(CustomReminderRepositoryImpl())
    private val techUpdateService = TechUpdateService(TechUpdateRepositoryImpl())
    private val stateStorage = InMemoryStateStorage()

    fun start() {
        val bot = bot {
            token = config.bot.token
            dispatch {
                // Stats recording for all text messages
                text {
                    val userId = message.from?.id ?: return@text
                    val msgText = message.text ?: return@text
                    StatsStorage.record(userId, msgText)
                }

                // Keyboard button handler (must be before FSM handlers)
                MenuKeyboardHandler(eventService, participantService, stateStorage, config.webApp.url).register(this)

                GroupLifecycleHandler(config.bot.username).register(this)

                // Group flow: @bot create event | ... | @users.
                GroupEventHandler(eventService, participantService, userService, config.bot.username).register(this)

                // FSM: event creation
                EventCreateHandler(eventService, stateStorage).register(this)

                // FSM: broadcast
                BroadcastHandler(eventService, participantService, stateStorage).register(this)

                // FSM: payment confirmation flow
                PaymentHandler(eventService, participantService, stateStorage).register(this)

                // Owner actions: edit, participants, custom notifications, cancel.
                EventManageHandler(eventService, participantService, userService, customReminderService, stateStorage).register(this)

                // Commands
                CancelCommand(stateStorage).register(this)
                StartCommand(userService, stateStorage, config.webApp.url).register(this)
                HelpCommand().register(this)
                CreateEventCommand(stateStorage, config.webApp.url).register(this)
                ListEventsCommand(eventService, participantService).register(this)
                MyEventsCommand(eventService, participantService).register(this)
                StatsCommand(config.webApp.url).register(this)
                BroadcastCommand(eventService, participantService).register(this)
                TechUpdatesCommand(techUpdateService).register(this)

                // Callbacks
                EventDetailCallback(eventService, participantService).register(this)
                JoinEventCallback(eventService, participantService).register(this)
                LeaveEventCallback(participantService, eventService).register(this)
            }
        }

        registerCommandMenus(bot)

        notifyTechUpdateIfNeeded(bot)
        EventReminderService(bot, eventService, participantService, customReminderService, techUpdateService).start()
        bot.startPolling()
    }

    private fun registerCommandMenus(bot: com.github.kotlintelegrambot.Bot) {
        val privateCommands = listOf(
            BotCommand("start", "Главное меню"),
            BotCommand("events", "Лента мероприятий"),
            BotCommand("new", "Создать мероприятие"),
            BotCommand("my", "Мои события и управление"),
            BotCommand("broadcast", "Уведомление участникам"),
            BotCommand("stats", "Экспериментальное приложение"),
            BotCommand("updates_on", "Включить тех-уведы"),
            BotCommand("updates_off", "Выключить тех-уведы"),
            BotCommand("help", "Помощь"),
            BotCommand("cancel", "Отменить действие"),
        )
        val groupCommands = listOf(
            BotCommand("gevents", "Мероприятия этой группы"),
            BotCommand("gnew", "Создать событие группы"),
            BotCommand("ginvite", "Пригласить участников"),
            BotCommand("updates_on", "Включить тех-уведы"),
            BotCommand("updates_off", "Выключить тех-уведы"),
        )

        bot.setMyCommands(privateCommands)
        setCommandsForScope(config.bot.token, privateCommands, """{"type":"all_private_chats"}""")
        setCommandsForScope(config.bot.token, groupCommands, """{"type":"all_group_chats"}""")
    }

    private fun setCommandsForScope(token: String, commands: List<BotCommand>, scopeJson: String) {
        runCatching {
            val body = """{"scope":$scopeJson,"commands":${commands.toTelegramJson()}}"""
            val connection = URL("https://api.telegram.org/bot$token/setMyCommands").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            connection.inputStream.use { it.readBytes() }
        }
    }

    private fun notifyTechUpdateIfNeeded(bot: com.github.kotlintelegrambot.Bot) {
        val releaseId = System.getenv("RELEASE_ID")
            ?: System.getenv("GITHUB_SHA")
            ?: System.getenv("APP_VERSION")
            ?: return
        if (!techUpdateService.shouldNotifyRelease(releaseId)) return
        techUpdateService.enabledChatIds().forEach { chatId ->
            runCatching {
                bot.sendMessage(
                    chatId = com.github.kotlintelegrambot.entities.ChatId.fromId(chatId),
                    text = "Вышло обновление!",
                )
            }
        }
    }

    private fun List<BotCommand>.toTelegramJson(): String =
        joinToString(prefix = "[", postfix = "]") {
            """{"command":"${it.command.json()}","description":"${it.description.json()}"}"""
        }

    private fun String.json(): String =
        replace("\\", "\\\\").replace("\"", "\\\"")
}
