package com.meventus

import com.meventus.bot.MeventusBot
import com.meventus.config.AppConfig
import com.meventus.domain.service.EventService
import com.meventus.domain.service.ParticipantService
import com.meventus.infrastructure.persistence.DatabaseFactory
import com.meventus.infrastructure.persistence.repository.EventRepositoryImpl
import com.meventus.infrastructure.persistence.repository.ParticipantRepositoryImpl
import com.meventus.webapp.WebAppServer

fun main() {
    val config = AppConfig.load()

    DatabaseFactory.init(config.database)

    val eventService = EventService(EventRepositoryImpl())
    val participantService = ParticipantService(ParticipantRepositoryImpl())

    WebAppServer.start(config.webApp.port, eventService, participantService, config.bot.token)

    MeventusBot(config).start()
}
