package com.meventus.bot.commands

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import com.meventus.bot.states.StateStorage
import com.meventus.bot.states.UserState
import com.meventus.domain.service.EventService

class CreateEventCommand(
    private val eventService: EventService,
    private val stateStorage: StateStorage,
) : Command {
    override val name = "new"

    override fun register(dispatcher: Dispatcher) {
        dispatcher.command(name) {
            val userId = message.from?.id ?: return@command
            stateStorage.set(userId, UserState.AwaitingEventTitle)
            bot.sendMessage(
                chatId = ChatId.fromId(message.chat.id),
                text = "Введите название мероприятия:",
            )
        }
    }
}
