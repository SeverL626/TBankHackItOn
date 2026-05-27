package com.meventus.bot.handlers

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.meventus.bot.states.StateStorage
import com.meventus.bot.states.UserState
import com.meventus.domain.service.EventService
import com.meventus.domain.service.ParticipantService

class BroadcastHandler(
    private val eventService: EventService,
    private val participantService: ParticipantService,
    private val stateStorage: StateStorage,
) {
    fun register(dispatcher: Dispatcher) {

        // Select event to broadcast
        dispatcher.callbackQuery {
            val data = callbackQuery.data ?: return@callbackQuery
            if (!data.startsWith("bcast:")) return@callbackQuery
            val eventId = data.removePrefix("bcast:").toLongOrNull() ?: return@callbackQuery
            val userId = callbackQuery.from.id
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery

            val event = eventService.findById(eventId)
            if (event == null || event.ownerId != userId) {
                bot.answerCallbackQuery(callbackQuery.id, "Нет доступа")
                return@callbackQuery
            }

            val count = participantService.listByEvent(eventId).size
            stateStorage.set(userId, UserState.AwaitingBroadcast(eventId))
            bot.answerCallbackQuery(callbackQuery.id)
            bot.sendMessage(
                chatId = ChatId.fromId(chatId),
                text = "📢 *${event.title}* — $count участников\n\nНапишите сообщение для рассылки:\n_/cancel — отменить_",
                parseMode = ParseMode.MARKDOWN,
            )
        }

        // Send broadcast message to all participants
        dispatcher.text {
            val userId = message.from?.id ?: return@text
            val state = stateStorage.get(userId) as? UserState.AwaitingBroadcast ?: return@text
            val text = message.text ?: return@text
            val chatId = ChatId.fromId(message.chat.id)

            if (text == "/cancel") {
                stateStorage.clear(userId)
                bot.sendMessage(chatId, "Рассылка отменена.")
                return@text
            }

            val event = eventService.findById(state.eventId)
            if (event == null) {
                stateStorage.clear(userId)
                bot.sendMessage(chatId, "Мероприятие не найдено.")
                return@text
            }

            val participants = participantService.listByEvent(state.eventId)
            stateStorage.clear(userId)

            if (participants.isEmpty() && event.groupChatId == null) {
                bot.sendMessage(chatId, "Нет участников для рассылки.")
                return@text
            }

            val broadcastText = "📢 *${event.title}*\n\n$text"
            var sent = 0
            if (event.groupChatId != null) {
                runCatching {
                    bot.sendMessage(
                        chatId = ChatId.fromId(event.groupChatId),
                        text = broadcastText,
                        parseMode = ParseMode.MARKDOWN,
                    )
                    sent++
                }
            } else {
                participants.forEach { p ->
                    runCatching {
                        bot.sendMessage(
                            chatId = ChatId.fromId(p.userId),
                            text = broadcastText,
                            parseMode = ParseMode.MARKDOWN,
                        )
                        sent++
                    }
                }
            }

            bot.sendMessage(
                chatId = chatId,
                text = if (event.groupChatId != null) {
                    "✅ Уведомление отправлено в группу."
                } else {
                    "✅ Рассылка отправлена: $sent/${participants.size} участников получили сообщение."
                },
            )
        }
    }
}
