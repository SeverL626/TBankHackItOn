package com.meventus.bot.callbacks

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.meventus.domain.service.ParticipantService

class LeaveEventCallback(private val participantService: ParticipantService) : CallbackHandler {
    override val prefix = "leave:"

    override fun register(dispatcher: Dispatcher) {
        dispatcher.callbackQuery {
            val data = callbackQuery.data ?: return@callbackQuery
            if (!data.startsWith(prefix)) return@callbackQuery

            val eventId = data.removePrefix(prefix).toLongOrNull() ?: return@callbackQuery
            val userId = callbackQuery.from.id
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val messageId = callbackQuery.message?.messageId ?: return@callbackQuery

            if (!participantService.isParticipant(eventId, userId)) {
                bot.answerCallbackQuery(callbackQuery.id, "Вы не участвуете в этом событии")
                return@callbackQuery
            }

            participantService.leave(eventId, userId)
            bot.answerCallbackQuery(callbackQuery.id, "Вы покинули мероприятие")
            bot.editMessageReplyMarkup(
                chatId = ChatId.fromId(chatId),
                messageId = messageId,
                replyMarkup = InlineKeyboardMarkup.create(
                    listOf(InlineKeyboardButton.CallbackData("✅ Участвовать", "ejoin:$eventId")),
                ),
            )
        }
    }
}
