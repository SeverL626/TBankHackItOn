package com.meventus.bot.commands

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.meventus.domain.service.EventService
import com.meventus.domain.service.ParticipantService
import com.meventus.util.DateUtils

class BroadcastCommand(
    private val eventService: EventService,
    private val participantService: ParticipantService,
) : Command {
    override val name = "broadcast"

    override fun register(dispatcher: Dispatcher) {
        dispatcher.command(name) {
            if (message.chat.id < 0) return@command
            val userId = message.from?.id ?: return@command
            val chatId = ChatId.fromId(message.chat.id)
            val events = eventService.listByOwner(userId)
            if (events.isEmpty()) {
                bot.sendMessage(chatId, "У вас нет организованных мероприятий для рассылки.")
                return@command
            }
            val buttons = events.map { e ->
                val count = participantService.listByEvent(e.id).size
                listOf(InlineKeyboardButton.CallbackData(
                    "📋 ${e.title} (${DateUtils.format(e.startsAt)}) — $count уч.",
                    "bcast:${e.id}",
                ))
            }
            bot.sendMessage(
                chatId = chatId,
                text = "📢 *Рассылка участникам*\n\nВыберите мероприятие, чьим участникам хотите написать:",
                parseMode = ParseMode.MARKDOWN,
                replyMarkup = InlineKeyboardMarkup.create(buttons),
            )
        }
    }
}
