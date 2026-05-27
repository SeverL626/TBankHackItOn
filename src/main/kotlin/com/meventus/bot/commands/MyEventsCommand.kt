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

class MyEventsCommand(
    private val eventService: EventService,
    private val participantService: ParticipantService,
) : Command {
    override val name = "my"

    override fun register(dispatcher: Dispatcher) {
        dispatcher.command(name) {
            if (message.chat.id < 0) return@command
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

            val text = buildString {
                appendLine("*Мои мероприятия*")
                appendLine("Открой карточку или управление кнопкой ниже.")
                appendLine()
                if (joined.isNotEmpty()) {
                    appendLine("*Участвую:*")
                    joined.forEachIndexed { index, event ->
                        appendLine("${index + 1}. ${event.title} · ${DateUtils.format(event.startsAt)}")
                    }
                    appendLine()
                }
                if (owned.isNotEmpty()) {
                    appendLine("*Организую:*")
                    owned.forEachIndexed { index, event ->
                        appendLine("${index + 1}. ${event.title} · ${DateUtils.format(event.startsAt)}")
                    }
                }
            }
            val rows = mutableListOf<List<InlineKeyboardButton>>()
            joined.forEach { event ->
                rows += listOf(InlineKeyboardButton.CallbackData("Открыть: ${event.title.take(24)}", "edetail:${event.id}"))
            }
            owned.forEach { event ->
                rows += listOf(InlineKeyboardButton.CallbackData("Управлять: ${event.title.take(22)}", "manage:${event.id}"))
            }
            bot.sendMessage(
                chatId = chatId,
                text = text,
                parseMode = ParseMode.MARKDOWN,
                replyMarkup = InlineKeyboardMarkup.create(rows),
            )
        }
    }
}
