package com.meventus.bot.commands

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.command
import com.meventus.bot.messages.Messages
import com.meventus.domain.service.UserService

class StartCommand(
    private val userService: UserService,
) : Command {
    override val name = "start"

    override fun register(dispatcher: Dispatcher) {
        dispatcher.command(name) {
            val from = message.from ?: return@command
            userService.registerIfAbsent(
                telegramId = from.id,
                username = from.username,
                firstName = from.firstName,
            )
            bot.sendMessage(
                chatId = com.github.kotlintelegrambot.entities.ChatId.fromId(message.chat.id),
                text = Messages.welcome(from.firstName),
            )
        }
    }
}
