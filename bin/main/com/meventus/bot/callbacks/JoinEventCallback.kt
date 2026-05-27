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
            if (event != null && event.ownerId == userId) {
                bot.answerCallbackQuery(callbackQuery.id, "Вы организатор этого мероприятия")
                return@callbackQuery
            }

            if (participantService.isParticipant(eventId, userId)) {
                bot.answerCallbackQuery(callbackQuery.id, "Вы уже участвуете!")
                return@callbackQuery
            }

            val ev = eventService.findById(eventId)
            if (ev != null && ev.paymentType == PaymentType.ADVANCE) {
                val costStr = if (ev.cost > 0) "*${ev.cost}₽*" else "бесплатно"
                bot.answerCallbackQuery(callbackQuery.id)
                bot.sendMessage(
                    chatId = ChatId.fromId(chatId),
                    text = "💳 *Оплата заранее через СБП*\n\n" +
                        "Сумма: $costStr\n" +
                        "Номер: `${ev.sbpPhone}`\n" +
                        "Получатель: *${ev.sbpName}*\n\n" +
                        "После перевода нажмите кнопку ниже — организатор получит уведомление и подтвердит оплату.",
                    parseMode = ParseMode.MARKDOWN,
                    replyMarkup = InlineKeyboardMarkup.create(
                        listOf(InlineKeyboardButton.CallbackData("📲 Я перевёл деньги", "pay_sent:$eventId")),
                    ),
                )
                return@callbackQuery
            }

            participantService.join(eventId, userId)
            bot.answerCallbackQuery(callbackQuery.id, "Вы записались! ✅")
            bot.editMessageReplyMarkup(
                chatId = ChatId.fromId(chatId),
                messageId = messageId,
                replyMarkup = InlineKeyboardMarkup.create(
                    listOf(InlineKeyboardButton.CallbackData("❌ Покинуть", "leave:$eventId")),
                ),
            )
        }
    }
}
