package com.meventus.bot.commands

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import com.meventus.bot.states.StateStorage
import com.meventus.bot.states.UserState

class CreateEventCommand(private val stateStorage: StateStorage) : Command {
    override val name = "new"

    override fun register(dispatcher: Dispatcher) {
        dispatcher.command(name) {
            val userId = message.from?.id ?: return@command
            stateStorage.set(userId, UserState.AwaitingEventTitle)
            bot.sendMessage(
                chatId = ChatId.fromId(message.chat.id),
                text = "Создаём мероприятие!\n\nШаг 1/7 — Введите *название*:",
                parseMode = com.github.kotlintelegrambot.entities.ParseMode.MARKDOWN,
            )
        }
    }
}
