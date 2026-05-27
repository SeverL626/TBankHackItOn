package com.meventus.bot.commands

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import com.meventus.domain.service.EventService
import com.meventus.domain.service.ParticipantService
import com.meventus.util.DateUtils

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

            val text = buildString {
                appendLine("*Вы организуете:*")
                if (owned.isEmpty()) appendLine("—")
                else owned.forEach { appendLine("• ${it.title} — ${DateUtils.format(it.startsAt)}") }
                appendLine()
                appendLine("*Вы участвуете:*")
                if (joined.isEmpty()) appendLine("—")
                else joined.forEach { appendLine("• ${it.title} — ${DateUtils.format(it.startsAt)}") }
            }
            bot.sendMessage(
                chatId = ChatId.fromId(message.chat.id),
                text = text,
                parseMode = com.github.kotlintelegrambot.entities.ParseMode.MARKDOWN,
            )
        }
    }
}
