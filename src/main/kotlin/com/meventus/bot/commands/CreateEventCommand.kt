package com.meventus.bot.commands

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.meventus.bot.states.StateStorage
import com.meventus.bot.states.UserState
import com.meventus.domain.model.EventVisibility

class CreateEventCommand(private val stateStorage: StateStorage) : Command {
    override val name = "new"

    override fun register(dispatcher: Dispatcher) {
        dispatcher.command(name) {
            val userId = message.from?.id ?: return@command
            val text = message.text.orEmpty()
            val visibility = if (text.contains("private", ignoreCase = true) || text.contains("приват", ignoreCase = true)) {
                EventVisibility.PRIVATE
            } else {
                EventVisibility.PUBLIC
            }
            stateStorage.set(userId, UserState.AwaitingEventTitle(visibility))
            val visibilityText = if (visibility == EventVisibility.PRIVATE) "приватное" else "публичное"
            bot.sendMessage(
                chatId = ChatId.fromId(message.chat.id),
                text = "Создаём $visibilityText мероприятие. _/cancel — отменить_\n\nШаг 1/8 — введите *название*:",
                parseMode = ParseMode.MARKDOWN,
            )
        }
    }
}
