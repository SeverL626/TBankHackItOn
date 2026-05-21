package com.meventus.bot.handlers

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.meventus.bot.states.StateStorage
import com.meventus.bot.states.UserState
import com.meventus.domain.service.EventService
import com.meventus.domain.service.ParticipantService

class PaymentHandler(
    private val eventService: EventService,
    private val participantService: ParticipantService,
    private val stateStorage: StateStorage,
) {
    fun register(dispatcher: Dispatcher) {

        // Step 1: user clicked "Я перевёл" — ask for their phone
        dispatcher.callbackQuery {
            val data = callbackQuery.data ?: return@callbackQuery
            if (!data.startsWith("pay_sent:")) return@callbackQuery
            val eventId = data.removePrefix("pay_sent:").toLongOrNull() ?: return@callbackQuery
            val userId = callbackQuery.from.id
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery

            if (participantService.isParticipant(eventId, userId)) {
                bot.answerCallbackQuery(callbackQuery.id, "Вы уже отправили подтверждение")
                return@callbackQuery
            }

            stateStorage.set(userId, UserState.AwaitingPaymentPhone(eventId))
            bot.answerCallbackQuery(callbackQuery.id)
            bot.sendMessage(
                chatId = ChatId.fromId(chatId),
                text = "Введите *номер телефона*, с которого был отправлен перевод (например: `+79991234567`):\n\n_/cancel — отменить_",
                parseMode = ParseMode.MARKDOWN,
            )
        }

        // Steps 2 & 3: phone then name — single handler to avoid double-firing
        dispatcher.text {
            val userId = message.from?.id ?: return@text
            val text = message.text?.trim() ?: return@text
            val chatId = ChatId.fromId(message.chat.id)

            when (val state = stateStorage.get(userId)) {
                is UserState.AwaitingPaymentPhone -> {
                    stateStorage.set(userId, UserState.AwaitingPaymentName(state.eventId, text))
                    bot.sendMessage(
                        chatId = chatId,
                        text = "Введите *ваше имя* (как отправитель перевода):",
                        parseMode = ParseMode.MARKDOWN,
                    )
                }

                is UserState.AwaitingPaymentName -> {
                    val event = eventService.findById(state.eventId)
                    if (event == null) {
                        stateStorage.clear(userId)
                        bot.sendMessage(chatId, "Мероприятие не найдено.")
                        return@text
                    }

                    stateStorage.clear(userId)
                    participantService.joinWithPayment(state.eventId, userId, state.phone, text)

                    bot.sendMessage(
                        chatId = chatId,
                        text = "✅ Ожидайте подтверждения оплаты от организатора.",
                    )

                    val userInfo = message.from?.let { u ->
                        val uname = if (u.username != null) " (@${u.username})" else ""
                        "${u.firstName}${uname}"
                    } ?: "Пользователь"

                    runCatching {
                        bot.sendMessage(
                            chatId = ChatId.fromId(event.ownerId),
                            text = "💰 *Запрос оплаты*\n\n" +
                                "Мероприятие: *${event.title}*\n" +
                                "Участник: $userInfo\n" +
                                "Номер отправителя: `${state.phone}`\n" +
                                "Имя отправителя: *$text*\n" +
                                "Сумма: *${event.cost}₽*\n\n" +
                                "Подтвердите получение перевода:",
                            parseMode = ParseMode.MARKDOWN,
                            replyMarkup = InlineKeyboardMarkup.create(
                                listOf(
                                    InlineKeyboardButton.CallbackData("✅ Подтвердить", "pay_ok:${state.eventId}:$userId"),
                                    InlineKeyboardButton.CallbackData("❌ Отклонить", "pay_rej:${state.eventId}:$userId"),
                                ),
                            ),
                        )
                    }
                }

                else -> return@text
            }
        }

        // Organizer confirms payment
        dispatcher.callbackQuery {
            val data = callbackQuery.data ?: return@callbackQuery
            if (!data.startsWith("pay_ok:")) return@callbackQuery
            val parts = data.removePrefix("pay_ok:").split(":")
            val eventId = parts.getOrNull(0)?.toLongOrNull() ?: return@callbackQuery
            val participantId = parts.getOrNull(1)?.toLongOrNull() ?: return@callbackQuery
            val organizerId = callbackQuery.from.id
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val messageId = callbackQuery.message?.messageId ?: return@callbackQuery

            val event = eventService.findById(eventId) ?: run {
                bot.answerCallbackQuery(callbackQuery.id, "Мероприятие не найдено")
                return@callbackQuery
            }
            if (event.ownerId != organizerId) {
                bot.answerCallbackQuery(callbackQuery.id, "Нет доступа")
                return@callbackQuery
            }

            participantService.confirmPayment(eventId, participantId, event.cost)
            bot.answerCallbackQuery(callbackQuery.id, "Оплата подтверждена ✅")
            bot.editMessageText(
                chatId = ChatId.fromId(chatId),
                messageId = messageId,
                text = callbackQuery.message?.text + "\n\n✅ *Оплата подтверждена*",
                parseMode = ParseMode.MARKDOWN,
            )

            // Notify participant
            runCatching {
                bot.sendMessage(
                    chatId = ChatId.fromId(participantId),
                    text = "✅ Ваша оплата за *${event.title}* подтверждена! Вы участник мероприятия.",
                    parseMode = ParseMode.MARKDOWN,
                )
            }
        }

        // Organizer rejects payment
        dispatcher.callbackQuery {
            val data = callbackQuery.data ?: return@callbackQuery
            if (!data.startsWith("pay_rej:")) return@callbackQuery
            val parts = data.removePrefix("pay_rej:").split(":")
            val eventId = parts.getOrNull(0)?.toLongOrNull() ?: return@callbackQuery
            val participantId = parts.getOrNull(1)?.toLongOrNull() ?: return@callbackQuery
            val organizerId = callbackQuery.from.id
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val messageId = callbackQuery.message?.messageId ?: return@callbackQuery

            val event = eventService.findById(eventId) ?: run {
                bot.answerCallbackQuery(callbackQuery.id, "Мероприятие не найдено")
                return@callbackQuery
            }
            if (event.ownerId != organizerId) {
                bot.answerCallbackQuery(callbackQuery.id, "Нет доступа")
                return@callbackQuery
            }

            participantService.rejectPayment(eventId, participantId)
            bot.answerCallbackQuery(callbackQuery.id, "Оплата отклонена")
            bot.editMessageText(
                chatId = ChatId.fromId(chatId),
                messageId = messageId,
                text = callbackQuery.message?.text + "\n\n❌ *Оплата отклонена*",
                parseMode = ParseMode.MARKDOWN,
            )

            // Notify participant
            runCatching {
                bot.sendMessage(
                    chatId = ChatId.fromId(participantId),
                    text = "❌ Ваша оплата за *${event.title}* отклонена. Свяжитесь с организатором для уточнения.",
                    parseMode = ParseMode.MARKDOWN,
                )
            }
        }
    }
}
