package com.meventus.bot.commands

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.meventus.bot.keyboards.CreateEventKeyboard
import com.meventus.bot.states.StateStorage
import com.meventus.bot.states.UserState

class CreateEventCommand(
    private val stateStorage: StateStorage,
    private val webAppUrl: String,
) : Command {
    override val name = "new"

    override fun register(dispatcher: Dispatcher) {
        dispatcher.command(name) {
            if (message.chat.id < 0) return@command
            val userId = message.from?.id ?: return@command
            stateStorage.set(userId, UserState.AwaitingEventTitle())
            bot.sendMessage(
                chatId = ChatId.fromId(message.chat.id),
                text = "Создание мероприятия.\n\nМожно пройти всё здесь, в чате. Мини-приложение пока экспериментальное, но в нём удобнее заполнять форму. Сначала выбери видимость.",
                parseMode = ParseMode.MARKDOWN,
                replyMarkup = CreateEventKeyboard.entry(webAppUrl),
            )
        }
    }
}
