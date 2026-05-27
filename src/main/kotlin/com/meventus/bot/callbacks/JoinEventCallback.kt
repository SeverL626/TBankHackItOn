package com.meventus.bot.callbacks

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.meventus.domain.model.PaymentType
import com.meventus.domain.service.EventService
import com.meventus.domain.service.ParticipantService

class JoinEventCallback(
    private val eventService: EventService,
    private val participantService: ParticipantService,
) : CallbackHandler {
    override val prefix = "ejoin:"

    override fun register(dispatcher: Dispatcher) {
        dispatcher.callbackQuery {
            val data = callbackQuery.data ?: return@callbackQuery
            if (!data.startsWith(prefix)) return@callbackQuery

            val eventId = data.removePrefix(prefix).toLongOrNull() ?: return@callbackQuery
            val userId = callbackQuery.from.id
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val messageId = callbackQuery.message?.messageId ?: return@callbackQuery

            val event = eventService.findById(eventId)
            if (event == null) {
                bot.answerCallbackQuery(callbackQuery.id, "Мероприятие не найдено")
                return@callbackQuery
            }
            if (event.ownerId == userId) {
                bot.answerCallbackQuery(callbackQuery.id, "Вы организатор этого мероприятия")
                return@callbackQuery
            }

            if (participantService.isParticipant(eventId, userId)) {
                bot.answerCallbackQuery(callbackQuery.id, "Вы уже участвуете!")
                return@callbackQuery
            }

            if (event.paymentType == PaymentType.ADVANCE) {
                val costStr = if (event.cost > 0) "*${event.cost}₽*" else "бесплатно"
                bot.answerCallbackQuery(callbackQuery.id)
                bot.sendMessage(
                    chatId = ChatId.fromId(chatId),
                    text = "💳 *Оплата заранее через СБП*\n\n" +
                        "Сумма: $costStr\n" +
                        "Номер: `${event.sbpPhone}`\n" +
                        "Получатель: *${event.sbpName}*\n\n" +
                        "После перевода нажмите кнопку ниже — организатор получит уведомление и подтвердит оплату.",
                    parseMode = ParseMode.MARKDOWN,
                    replyMarkup = InlineKeyboardMarkup.create(
                        listOf(InlineKeyboardButton.CallbackData("📲 Я перевёл деньги", "pay_sent:$eventId")),
                    ),
                )
                return@callbackQuery
            }

            participantService.join(eventId, userId)
            bot.answerCallbackQuery(callbackQuery.id, "Готово, вы записались ✅")
            val name = callbackQuery.from.username?.let { "@$it" } ?: callbackQuery.from.firstName
            runCatching {
                bot.sendMessage(
                    chatId = ChatId.fromId(event.ownerId),
                    text = "Новый участник: $name записался на *${event.title}*",
                    parseMode = ParseMode.MARKDOWN,
                )
            }
            bot.editMessageReplyMarkup(
                chatId = ChatId.fromId(chatId),
                messageId = messageId,
                replyMarkup = InlineKeyboardMarkup.create(
                    listOf(InlineKeyboardButton.CallbackData("🔍 Подробнее", "edetail:$eventId")),
                    listOf(InlineKeyboardButton.CallbackData("❌ Покинуть", "leave:$eventId")),
                ),
            )
        }
    }
}
