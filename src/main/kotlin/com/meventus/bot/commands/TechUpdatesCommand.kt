package com.meventus.bot.commands

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.meventus.domain.service.TechUpdateService

class TechUpdatesCommand(
    private val techUpdateService: TechUpdateService,
) : Command {
    override val name = "updates_on"

    override fun register(dispatcher: Dispatcher) {
        dispatcher.command("updates_on") {
            techUpdateService.enable(message.chat.id)
            bot.sendMessage(
                chatId = ChatId.fromId(message.chat.id),
                text = "✅ Тех-уведомления включены. При новом деплое напишу: *Вышло обновление!*",
                parseMode = ParseMode.MARKDOWN,
            )
        }

        dispatcher.command("updates_off") {
            techUpdateService.disable(message.chat.id)
            bot.sendMessage(
                chatId = ChatId.fromId(message.chat.id),
                text = "Тех-уведомления выключены.",
            )
        }
    }
}
