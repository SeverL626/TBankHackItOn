package com.meventus.bot.commands

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.KeyboardReplyMarkup
import com.github.kotlintelegrambot.entities.keyboard.KeyboardButton
import com.meventus.bot.messages.Messages
import com.meventus.domain.service.UserService

class StartCommand(
    private val userService: UserService,
) : Command {
    override val name = "start"

    private val menuKeyboard = KeyboardReplyMarkup(
        keyboard = listOf(
            listOf(KeyboardButton("📋 Мероприятия"), KeyboardButton("⭐ Мои события")),
            listOf(KeyboardButton("➕ Создать событие"), KeyboardButton("📢 Рассылка")),
            listOf(KeyboardButton("📊 Статистика")),
        ),
        resizeKeyboard = true,
    )

    override fun register(dispatcher: Dispatcher) {
        dispatcher.command(name) {
            val from = message.from ?: return@command
            userService.registerIfAbsent(
                telegramId = from.id,
                username = from.username,
                firstName = from.firstName,
            )
            bot.sendMessage(
                chatId = ChatId.fromId(message.chat.id),
                text = Messages.welcome(from.firstName),
                replyMarkup = menuKeyboard,
            )
        }
    }
}
