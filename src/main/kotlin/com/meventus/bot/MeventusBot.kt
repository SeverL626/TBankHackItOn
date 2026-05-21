package com.meventus.bot

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.meventus.bot.commands.CreateEventCommand
import com.meventus.bot.commands.HelpCommand
import com.meventus.bot.commands.ListEventsCommand
import com.meventus.bot.commands.MyEventsCommand
import com.meventus.bot.commands.StartCommand
import com.meventus.bot.states.InMemoryStateStorage
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
                StartCommand(userService).register(this)
                HelpCommand().register(this)
                CreateEventCommand(eventService, stateStorage).register(this)
                ListEventsCommand(eventService).register(this)
                MyEventsCommand(eventService, participantService).register(this)
            }
        }
        bot.startPolling()
    }
}
