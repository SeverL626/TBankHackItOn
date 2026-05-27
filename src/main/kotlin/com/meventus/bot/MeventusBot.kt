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
import com.meventus.bot.handlers.BroadcastHandler
import com.meventus.bot.handlers.EventCreateHandler
import com.meventus.bot.handlers.GroupEventHandler
import com.meventus.bot.handlers.MenuKeyboardHandler
import com.meventus.bot.handlers.PaymentHandler
import com.meventus.bot.notifications.EventReminderService
import com.meventus.bot.states.InMemoryStateStorage
import com.meventus.bot.stats.StatsStorage
import com.meventus.config.AppConfig
import com.meventus.domain.service.EventService
import com.meventus.domain.service.ParticipantService
import com.meventus.domain.service.UserService
import com.meventus.infrastructure.persistence.repository.EventRepositoryImpl
import com.meventus.infrastructure.persistence.repository.ParticipantRepositoryImpl
import com.meventus.infrastructure.persistence.repository.UserRepositoryImpl

class MeventusBot(private val config: AppConfig) {

    private val userService = UserService(UserRepositoryImpl())
    private val eventService = EventService(EventRepositoryImpl())
    private val participantService = ParticipantService(ParticipantRepositoryImpl())
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

                // Group flow: @bot create event | ... | @users.
                GroupEventHandler(eventService, participantService, userService, config.bot.username).register(this)

                // FSM: event creation
                EventCreateHandler(eventService, stateStorage).register(this)

                // FSM: broadcast
                BroadcastHandler(eventService, participantService, stateStorage).register(this)

                // FSM: payment confirmation flow
                PaymentHandler(eventService, participantService, stateStorage).register(this)

                // Commands
                CancelCommand(stateStorage).register(this)
                StartCommand(userService).register(this)
                HelpCommand().register(this)
                CreateEventCommand(stateStorage).register(this)
                ListEventsCommand(eventService, participantService).register(this)
                MyEventsCommand(eventService, participantService).register(this)
                StatsCommand(config.webApp.url).register(this)
                BroadcastCommand(eventService, participantService).register(this)

                // Callbacks
                EventDetailCallback(eventService, participantService).register(this)
                JoinEventCallback(eventService, participantService).register(this)
                LeaveEventCallback(participantService, eventService).register(this)
            }
        }

        // Register bot command menu (shown when user types /)
        bot.setMyCommands(
            listOf(
                BotCommand("cancel", "Отменить текущее действие"),
                BotCommand("start", "Главное меню"),
                BotCommand("events", "Список мероприятий"),
                BotCommand("new", "Создать мероприятие"),
                BotCommand("my", "Мои мероприятия"),
                BotCommand("broadcast", "Рассылка участникам"),
                BotCommand("group_new", "Создать мероприятие из группы"),
                BotCommand("gevents", "Мероприятия текущей группы"),
                BotCommand("ghelp", "Помощь по групповому режиму"),
                BotCommand("stats", "Статистика и мини-приложение"),
                BotCommand("help", "Помощь"),
            ),
        )

        EventReminderService(bot, eventService, participantService).start()
        bot.startPolling()
    }
}
