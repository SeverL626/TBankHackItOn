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
import com.meventus.domain.model.EventRegistrationMode
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
            val userId = message.from?.id
            sendEventList(bot, ChatId.fromId(message.chat.id), eventService.listUpcoming(), participantService, userId)
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
                    sendEventCard(bot, ChatId.fromId(chatId), event, participantService, callbackQuery.from.id)
                }
            }
        }
    }

    companion object {
        fun sendEventList(
            bot: com.github.kotlintelegrambot.Bot,
            chatId: ChatId,
            events: List<Event>,
            participantService: ParticipantService,
            userId: Long? = null,
            limit: Int = 10,
        ) {
            if (events.isEmpty()) {
                bot.sendMessage(
                    chatId = chatId,
                    text = "Пока нет ближайших публичных мероприятий.\n\nМожно создать своё: *➕ Создать* или /new.",
                    parseMode = ParseMode.MARKDOWN,
                )
                return
            }

            bot.sendMessage(
                chatId = chatId,
                text = "Нашёл ближайшие мероприятия. В карточке нажми *✅ Участвовать*, чтобы записаться.",
                parseMode = ParseMode.MARKDOWN,
            )
            events.take(limit).forEach { event ->
                sendEventCard(bot, chatId, event, participantService, userId)
            }
            bot.sendMessage(
                chatId = chatId,
                text = "Нужно сузить поиск? Выбери теги и нажми *Найти*.",
                parseMode = ParseMode.MARKDOWN,
                replyMarkup = TagKeyboard.forFilter(emptySet()),
            )
        }

        fun sendEventCard(
            bot: com.github.kotlintelegrambot.Bot,
            chatId: ChatId,
            event: Event,
            participantService: ParticipantService,
            userId: Long? = null,
        ) {
            val participantCount = participantService.listByEvent(event.id).size
            val isJoined = userId != null && participantService.isParticipant(event.id, userId)
            val isOwner = userId != null && event.ownerId == userId
            val costText = if (event.cost == 0L) "Бесплатно" else "${event.cost} ₽"
            val tagsText = event.tags.joinToString(" ") { "${it.emoji} ${it.displayName}" }
            val roleText = when {
                isOwner -> "👑 Вы организатор"
                isJoined -> "✅ Вы участвуете"
                event.registrationMode == EventRegistrationMode.INVITE_ONLY -> "🔐 Запись по приглашению"
                else -> "Можно записаться"
            }

            val text = buildString {
                appendLine("📌 *${event.title}*")
                if (tagsText.isNotEmpty()) appendLine(tagsText)
                appendLine(roleText)
                appendLine()
                if (event.shortDescription.isNotBlank()) appendLine(event.shortDescription)
                appendLine()
                appendLine("📍 ${event.address}")
                appendLine("📅 ${DateUtils.format(event.startsAt)}")
                appendLine("💰 $costText  👥 $participantCount чел.")
            }

            val actionRows = if (isOwner || (!isJoined && event.registrationMode == EventRegistrationMode.INVITE_ONLY)) {
                listOf(listOf(InlineKeyboardButton.CallbackData("🔍 Подробнее", "edetail:${event.id}")))
            } else {
                val joinButton = if (isJoined) {
                    InlineKeyboardButton.CallbackData("❌ Покинуть", "leave:${event.id}")
                } else {
                    InlineKeyboardButton.CallbackData("✅ Участвовать", "ejoin:${event.id}")
                }
                listOf(
                    listOf(InlineKeyboardButton.CallbackData("🔍 Подробнее", "edetail:${event.id}")),
                    listOf(joinButton),
                )
            }
            val markup = InlineKeyboardMarkup.create(actionRows)

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
