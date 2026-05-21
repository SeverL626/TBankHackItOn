package com.meventus.bot.commands

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import com.meventus.bot.messages.Messages

class HelpCommand : Command {
    override val name = "help"

    override fun register(dispatcher: Dispatcher) {
        dispatcher.command(name) {
            bot.sendMessage(
                chatId = ChatId.fromId(message.chat.id),
                text = Messages.HELP,
            )
        }
    }
}
