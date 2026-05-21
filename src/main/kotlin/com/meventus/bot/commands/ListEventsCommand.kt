package com.meventus.bot.commands

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import com.meventus.domain.service.EventService

class ListEventsCommand(
    private val eventService: EventService,
) : Command {
    override val name = "events"

    override fun register(dispatcher: Dispatcher) {
        dispatcher.command(name) {
            val events = eventService.listUpcoming()
            val text = if (events.isEmpty()) {
                "Пока нет ни одного предстоящего мероприятия."
            } else {
                events.joinToString("\n\n") { "• ${it.title} — ${it.startsAt}" }
            }
            bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = text)
        }
    }
}
