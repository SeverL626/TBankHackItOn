package com.meventus.bot.commands

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.meventus.domain.service.EventService
import com.meventus.domain.service.ParticipantService

class MyEventsCommand(
    private val eventService: EventService,
    private val participantService: ParticipantService,
) : Command {
    override val name = "my"

    override fun register(dispatcher: Dispatcher) {
        dispatcher.command(name) {
            val userId = message.from?.id ?: return@command
            val owned = eventService.listByOwner(userId)
            val joined = participantService.listEventsByUser(userId)
                .filter { it.ownerId != userId }

            val chatId = ChatId.fromId(message.chat.id)
            if (owned.isEmpty() && joined.isEmpty()) {
                bot.sendMessage(
                    chatId = chatId,
                    text = "У вас пока нет мероприятий.\n\nЧтобы записаться, нажмите *🔎 Найти* или /events.\nЧтобы создать своё — *➕ Создать* или /new.",
                    parseMode = ParseMode.MARKDOWN,
                )
                return@command
            }

            if (joined.isNotEmpty()) {
                bot.sendMessage(chatId, "*Вы участвуете:*", parseMode = ParseMode.MARKDOWN)
                joined.forEach { ListEventsCommand.sendEventCard(bot, chatId, it, participantService, userId) }
            }

            if (owned.isNotEmpty()) {
                bot.sendMessage(chatId, "*Вы организуете:*", parseMode = ParseMode.MARKDOWN)
                owned.forEach { ListEventsCommand.sendEventCard(bot, chatId, it, participantService, userId) }
            }
        }
    }
}
