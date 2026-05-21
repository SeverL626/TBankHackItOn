package com.meventus.bot

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.text
import com.meventus.bot.callbacks.EventDetailCallback
import com.meventus.bot.callbacks.JoinEventCallback
import com.meventus.bot.callbacks.LeaveEventCallback
import com.meventus.bot.commands.CreateEventCommand
import com.meventus.bot.commands.HelpCommand
import com.meventus.bot.commands.ListEventsCommand
import com.meventus.bot.commands.MyEventsCommand
import com.meventus.bot.commands.StartCommand
import com.meventus.bot.commands.StatsCommand
import com.meventus.bot.handlers.EventCreateHandler
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
                // Считаем статистику для всех текстовых сообщений
                text {
                    val userId = message.from?.id ?: return@text
                    val msgText = message.text ?: return@text
                    StatsStorage.record(userId, msgText)
                }

                // FSM создания события (text + photo + callbacks)
                EventCreateHandler(eventService, stateStorage).register(this)

                // Команды
                StartCommand(userService).register(this)
                HelpCommand().register(this)
                CreateEventCommand(stateStorage).register(this)
                ListEventsCommand(eventService, participantService).register(this)
                MyEventsCommand(eventService, participantService).register(this)
                StatsCommand(config.webApp.url).register(this)

                // Callbacks
                EventDetailCallback(eventService, participantService).register(this)
                JoinEventCallback(participantService).register(this)
                LeaveEventCallback(participantService).register(this)
            }
        }
        bot.startPolling()
    }
}
