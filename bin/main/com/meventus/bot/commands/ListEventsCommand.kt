package com.meventus.bot.commands

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.TelegramFile
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.meventus.bot.keyboards.TagKeyboard
import com.meventus.domain.model.Event
import com.meventus.domain.service.EventService
import com.meventus.domain.service.ParticipantService
import com.meventus.util.DateUtils

class ListEventsCommand(
    private val eventService: EventService,
    private val participantService: ParticipantService,
) : Command {
    override val name = "events"

    override fun register(dispatcher: Dispatcher) {

        // /events → показать фильтр тегов
        dispatcher.command(name) {
            bot.sendMessage(
                chatId = ChatId.fromId(message.chat.id),
                text = "Выберите теги для фильтрации (или сразу нажмите *Показать все*):",
                parseMode = ParseMode.MARKDOWN,
                replyMarkup = TagKeyboard.forFilter(emptySet()),
            )
        }

        // toggle тега в фильтре
        dispatcher.callbackQuery {
            val data = callbackQuery.data ?: return@callbackQuery
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val messageId = callbackQuery.message?.messageId ?: return@callbackQuery

            if (data.startsWith("filter:")) {
                val newTags = TagKeyboard.parseBitmask(data.removePrefix("filter:"))
                bot.answerCallbackQuery(callbackQuery.id)
                bot.editMessageReplyMarkup(
                    chatId = ChatId.fromId(chatId),
                    messageId = messageId,
                    replyMarkup = TagKeyboard.forFilter(newTags),
                )
            }

            if (data.startsWith("fsearch:")) {
                val tags = TagKeyboard.parseBitmask(data.removePrefix("fsearch:"))
                val events = eventService.listByTags(tags)
                bot.answerCallbackQuery(callbackQuery.id)
                if (events.isEmpty()) {
                    bot.editMessageText(
                        chatId = ChatId.fromId(chatId),
                        messageId = messageId,
                        text = "Мероприятий по выбранным тегам не найдено.\n\nПопробуй /events снова.",
                    )
                    return@callbackQuery
                }
                // Удаляем сообщение с фильтром и отправляем карточки событий
                bot.deleteMessage(ChatId.fromId(chatId), messageId)
                events.forEach { event ->
                    sendEventCard(bot, ChatId.fromId(chatId), event, participantService)
                }
            }
        }
    }

    companion object {
        fun sendEventCard(
            bot: com.github.kotlintelegrambot.Bot,
            chatId: ChatId,
            event: Event,
            participantService: ParticipantService,
            userId: Long? = null,
        ) {
            val participantCount = participantService.listByEvent(event.id).size
            val isJoined = userId != null && participantService.isParticipant(event.id, userId)
            val costText = if (event.cost == 0L) "Бесплатно" else "${event.cost} ₽"
            val tagsText = event.tags.joinToString(" ") { "${it.emoji} ${it.displayName}" }

            val text = buildString {
                appendLine("📌 *${event.title}*")
                if (tagsText.isNotEmpty()) appendLine(tagsText)
                appendLine()
                appendLine(event.shortDescription)
                appendLine()
                appendLine("📍 ${event.address}")
                appendLine("📅 ${DateUtils.format(event.startsAt)}")
                appendLine("💰 $costText  👥 $participantCount чел.")
            }

            val joinButton = if (isJoined) {
                InlineKeyboardButton.CallbackData("❌ Покинуть", "leave:${event.id}")
            } else {
                InlineKeyboardButton.CallbackData("✅ Участвовать", "ejoin:${event.id}")
            }
            val markup = InlineKeyboardMarkup.create(
                listOf(InlineKeyboardButton.CallbackData("🔍 Подробнее", "edetail:${event.id}")),
                listOf(joinButton),
            )

            if (event.photoFileId != null) {
                bot.sendPhoto(
                    chatId = chatId,
                    photo = TelegramFile.ByFileId(event.photoFileId),
                    caption = text,
                    parseMode = ParseMode.MARKDOWN,
                    replyMarkup = markup,
                )
            } else {
                bot.sendMessage(
                    chatId = chatId,
                    text = text,
                    parseMode = ParseMode.MARKDOWN,
                    replyMarkup = markup,
                )
            }
        }
    }
}
