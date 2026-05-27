package com.meventus.bot.commands

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.meventus.bot.cleanup.MessageCleaner
import com.meventus.bot.messages.Messages

class HelpCommand : Command {
    override val name = "help"

    override fun register(dispatcher: Dispatcher) {
        dispatcher.command(name) {
            val chatId = message.chat.id
            val isGroup = chatId < 0
            if (isGroup) {
                MessageCleaner.deleteLater(bot, chatId, message.messageId, 20)
                return@command
            }
            bot.sendMessage(
                chatId = ChatId.fromId(message.chat.id),
                text = Messages.HELP,
                parseMode = ParseMode.MARKDOWN,
            )
        }
    }
}
