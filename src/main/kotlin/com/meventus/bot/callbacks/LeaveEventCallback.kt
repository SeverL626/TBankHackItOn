package com.meventus.bot.callbacks

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.meventus.domain.service.ParticipantService

class LeaveEventCallback(
    private val participantService: ParticipantService,
) : CallbackHandler {
    override val prefix = "leave:"

    override fun register(dispatcher: Dispatcher) {
        // dispatcher.callbackQuery(prefix) { ... }
    }
}
