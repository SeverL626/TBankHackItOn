package com.meventus.bot.callbacks

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.TelegramFile
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.meventus.domain.service.EventService
import com.meventus.domain.service.ParticipantService
import com.meventus.util.DateUtils

class EventDetailCallback(
    private val eventService: EventService,
    private val participantService: ParticipantService,
) : CallbackHandler {
    override val prefix = "edetail:"

    override fun register(dispatcher: Dispatcher) {
        dispatcher.callbackQuery {
            val data = callbackQuery.data ?: return@callbackQuery
            if (!data.startsWith(prefix)) return@callbackQuery

            val eventId = data.removePrefix(prefix).toLongOrNull() ?: return@callbackQuery
            val userId = callbackQuery.from.id
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery

            val event = eventService.findById(eventId)
            if (event == null) {
                bot.answerCallbackQuery(callbackQuery.id, "Мероприятие не найдено")
                return@callbackQuery
            }

            val participants = participantService.listByEvent(eventId)
            val isJoined = participantService.isParticipant(eventId, userId)
            val costText = if (event.cost == 0L) "Бесплатно" else "${event.cost} ₽"
            val tagsText = event.tags.joinToString(" ") { "${it.emoji} ${it.displayName}" }

            val text = buildString {
                appendLine("*${event.title}*")
                if (tagsText.isNotEmpty()) appendLine(tagsText)
                appendLine()
                appendLine(event.description)
                appendLine()
                appendLine("📍 *Адрес:* ${event.address}")
                appendLine("📅 *Дата:* ${DateUtils.format(event.startsAt)}")
                appendLine("💰 *Стоимость:* $costText")
                appendLine("👥 *Участников:* ${participants.size}")
            }

            val joinButton = if (isJoined) {
                InlineKeyboardButton.CallbackData("❌ Покинуть", "leave:$eventId")
            } else {
                InlineKeyboardButton.CallbackData("✅ Участвовать", "ejoin:$eventId")
            }
            val markup = InlineKeyboardMarkup.create(listOf(joinButton))

            bot.answerCallbackQuery(callbackQuery.id)

            if (event.photoFileId != null) {
                bot.sendPhoto(
                    chatId = ChatId.fromId(chatId),
                    photo = TelegramFile.ByFileId(event.photoFileId),
                    caption = text,
                    parseMode = ParseMode.MARKDOWN,
                    replyMarkup = markup,
                )
            } else {
                bot.sendMessage(
                    chatId = ChatId.fromId(chatId),
                    text = text,
                    parseMode = ParseMode.MARKDOWN,
                    replyMarkup = markup,
                )
            }
        }
    }
}
