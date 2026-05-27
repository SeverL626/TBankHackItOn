package com.meventus.bot.handlers

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.meventus.bot.cleanup.MessageCleaner
import com.meventus.bot.messages.Messages
import com.meventus.domain.model.Event
import com.meventus.domain.model.EventRegistrationMode
import com.meventus.domain.model.EventTag
import com.meventus.domain.model.EventVisibility
import com.meventus.domain.service.EventService
import com.meventus.domain.service.ParticipantService
import com.meventus.domain.service.UserService
import com.meventus.util.DateUtils

class GroupEventHandler(
    private val eventService: EventService,
    private val participantService: ParticipantService,
    private val userService: UserService,
    private val botUsername: String,
) {
    fun register(dispatcher: Dispatcher) {
        dispatcher.text {
            val text = message.text ?: return@text
            val from = message.from ?: return@text
            val chatId = message.chat.id
            if (chatId > 0) return@text

            val username = botUsername.removePrefix("@")
            val mentionsBot = text.contains("@$username", ignoreCase = true)
            val command = text.substringBefore(" ").substringBefore("@").lowercase()
            val normalized = text.lowercase()

            when {
                command in setOf("/ghelp", "/group_help") ||
                    (mentionsBot && ("помощ" in normalized || "как" in normalized)) -> {
                    MessageCleaner.deleteLater(bot, chatId, message.messageId, 20)
                    sendTemporary(bot, ChatId.fromId(chatId), chatId, Messages.GROUP_HELP, 180)
                }

                command in setOf("/gevents", "/group_events") ||
                    (mentionsBot && ("мероприят" in normalized || "событ" in normalized || "афиша" in normalized)) -> {
                    MessageCleaner.deleteLater(bot, chatId, message.messageId, 20)
                    sendGroupEvents(bot, ChatId.fromId(chatId), chatId)
                }

                command in setOf("/ginvite", "/group_invite") ||
                    (mentionsBot && ("приглас" in normalized || "добав" in normalized)) -> {
                    MessageCleaner.deleteLater(bot, chatId, message.messageId, 45)
                    userService.registerIfAbsent(from.id, from.username, from.firstName)
                    inviteToGroupEvent(bot, ChatId.fromId(chatId), chatId, text, username, from.id)
                }

                command in setOf("/gnew", "/group_new") ||
                    (mentionsBot && ("созд" in normalized || "заплан" in normalized)) -> {
                    MessageCleaner.deleteLater(bot, chatId, message.messageId, 45)
                    userService.registerIfAbsent(from.id, from.username, from.firstName)
                    sendGroupCreateEntry(bot, ChatId.fromId(chatId), chatId)
                }
            }
        }

        dispatcher.callbackQuery {
            val data = callbackQuery.data ?: return@callbackQuery
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            if (chatId > 0) return@callbackQuery
            val messageId = callbackQuery.message?.messageId ?: return@callbackQuery

            when {
                data.startsWith("gpage:") -> {
                    val page = data.removePrefix("gpage:").toIntOrNull() ?: 0
                    bot.answerCallbackQuery(callbackQuery.id)
                    sendGroupEventsPage(bot, ChatId.fromId(chatId), chatId, page, messageId)
                }

                data.startsWith("gdetail:") -> {
                    val parts = data.removePrefix("gdetail:").split(":")
                    val eventId = parts.getOrNull(0)?.toLongOrNull() ?: return@callbackQuery
                    val page = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    bot.answerCallbackQuery(callbackQuery.id)
                    sendGroupEventDetail(bot, ChatId.fromId(chatId), chatId, eventId, page, messageId)
                }
            }
        }
    }

    private fun sendGroupCreateEntry(bot: Bot, chatId: ChatId, groupChatId: Long) {
        val payload = "gnew_m${kotlin.math.abs(groupChatId)}"
        val url = "https://t.me/${botUsername.removePrefix("@")}?start=$payload"
        val result = bot.sendMessage(
            chatId = chatId,
            text = "Создание мероприятия лучше пройти в личке, чтобы не засорять чат.\n\nПосле создания я пришлю сюда короткое уведомление.",
            parseMode = ParseMode.MARKDOWN,
            replyMarkup = InlineKeyboardMarkup.create(
                listOf(InlineKeyboardButton.Url("➕ Создать в личке", url)),
            ),
        )
        MessageCleaner.deleteLater(bot, groupChatId, result, 180)
    }

    private fun createGroupEvent(
        bot: Bot,
        chatId: ChatId,
        groupChatId: Long,
        text: String,
        botUsername: String,
        ownerId: Long,
    ) {
        val parsed = parse(text, botUsername)
        if (parsed == null) {
            sendTemporary(bot, chatId, groupChatId, Messages.GROUP_CREATE_HELP, 180)
            return
        }

        val startsAt = runCatching { DateUtils.parse(parsed.date) }.getOrNull()
        if (startsAt == null) {
            sendTemporary(bot, chatId, groupChatId, "Не понял дату. Нужен формат: `25.05.2026 18:00`", 90)
            return
        }

        val event = eventService.create(
            ownerId = ownerId,
            title = parsed.title,
            shortDescription = "Событие этой группы",
            description = parsed.description.ifBlank { "Групповое мероприятие" },
            address = parsed.address,
            startsAt = startsAt,
            cost = parsed.cost,
            photoFileId = null,
            tags = parsed.tags,
            visibility = parsed.visibility,
            registrationMode = parsed.registrationMode,
            groupChatId = groupChatId,
        )

        val registered = parsed.usernames.mapNotNull { usernameMention ->
            userService.findByUsername(usernameMention)?.also {
                participantService.joinIfAbsent(event.id, it.telegramId)
                runCatching {
                    bot.sendMessage(
                        chatId = ChatId.fromId(it.telegramId),
                        text = "Вас пригласили на *${event.title}* из группового чата.\nДата: ${DateUtils.format(event.startsAt)}",
                        parseMode = ParseMode.MARKDOWN,
                    )
                }
            }
        }
        val registeredNames = registered.mapNotNull { it.username }.toSet()
        val missing = parsed.usernames.filter { it.removePrefix("@") !in registeredNames }

        sendTemporary(bot, chatId, groupChatId, createdSummary(event, registered.size, missing), 300)
        sendGroupEventCard(bot, chatId, groupChatId, event)
    }

    private fun sendGroupEvents(bot: Bot, chatId: ChatId, groupChatId: Long) {
        sendGroupEventsPage(bot, chatId, groupChatId, page = 0, messageId = null)
    }

    private fun sendGroupEventsPage(bot: Bot, chatId: ChatId, groupChatId: Long, page: Int, messageId: Long?) {
        val events = eventService.listByGroup(groupChatId)
        if (events.isEmpty()) {
            sendTemporary(
                bot,
                chatId,
                groupChatId,
                "В этой группе пока нет ближайших мероприятий.\n\nСоздать: `/gnew Демо-день | 25.05.2026 18:00 | офис`",
                180,
            )
            return
        }

        val totalPages = ((events.size - 1) / GROUP_PAGE_SIZE) + 1
        val safePage = page.coerceIn(0, totalPages - 1)
        val pageEvents = events.drop(safePage * GROUP_PAGE_SIZE).take(GROUP_PAGE_SIZE)
        val text = buildString {
            appendLine("*Мероприятия группы*")
            appendLine("Страница ${safePage + 1}/$totalPages")
            appendLine()
            pageEvents.forEachIndexed { index, event ->
                val participants = participantService.listByEvent(event.id).size
                val mode = if (event.registrationMode == EventRegistrationMode.FREE) "свободная" else "по приглашению"
                appendLine("${safePage * GROUP_PAGE_SIZE + index + 1}. *${event.title}*")
                appendLine("   ID `${event.id}` · ${DateUtils.format(event.startsAt)} · $participants участн. · $mode")
            }
        }
        val rows = pageEvents.map { event ->
            listOf(InlineKeyboardButton.CallbackData("ID ${event.id}: ${event.title.take(24)}", "gdetail:${event.id}:$safePage"))
        }.toMutableList()
        val nav = mutableListOf<InlineKeyboardButton>()
        if (safePage > 0) nav += InlineKeyboardButton.CallbackData("◀", "gpage:${safePage - 1}")
        if (safePage < totalPages - 1) nav += InlineKeyboardButton.CallbackData("▶", "gpage:${safePage + 1}")
        if (nav.isNotEmpty()) rows += nav
        rows += listOf(InlineKeyboardButton.Url("➕ Создать в личке", "https://t.me/${botUsername.removePrefix("@")}?start=gnew_m${kotlin.math.abs(groupChatId)}"))

        if (messageId == null) {
            val result = bot.sendMessage(
                chatId = chatId,
                text = text,
                parseMode = ParseMode.MARKDOWN,
                replyMarkup = InlineKeyboardMarkup.create(rows),
            )
            MessageCleaner.deleteLater(bot, groupChatId, result, 10 * 60)
        } else {
            bot.editMessageText(
                chatId = chatId,
                messageId = messageId,
                text = text,
                parseMode = ParseMode.MARKDOWN,
                replyMarkup = InlineKeyboardMarkup.create(rows),
            )
        }
    }

    private fun sendGroupEventDetail(bot: Bot, chatId: ChatId, groupChatId: Long, eventId: Long, page: Int, messageId: Long) {
        val event = eventService.findById(eventId)
        if (event == null || event.groupChatId != groupChatId) {
            return
        }
        val participants = participantService.listByEvent(event.id).size
        val visibility = if (event.visibility == EventVisibility.PRIVATE) "🔒 только группа" else "🌍 публичное"
        val registration = if (event.registrationMode == EventRegistrationMode.FREE) {
            "свободная запись"
        } else {
            "по приглашению"
        }
        val tags = event.tags.joinToString(" ") { "${it.emoji} ${it.displayName}" }
        val text = buildString {
            appendLine("📌 *${event.title}*")
            appendLine("ID: `${event.id}`")
            appendLine("$visibility · $registration")
            if (tags.isNotBlank()) appendLine(tags)
            appendLine()
            appendLine(event.description)
            appendLine()
            appendLine("📅 ${DateUtils.format(event.startsAt)}")
            appendLine("📍 ${event.address}")
            appendLine("👥 $participants участн.")
        }
        val rows = mutableListOf<List<InlineKeyboardButton>>()
        if (event.registrationMode == EventRegistrationMode.FREE) {
            listOf(
                InlineKeyboardButton.CallbackData("✅ Участвовать", "ejoin:${event.id}"),
            ).also { rows += it }
        }
        rows += listOf(InlineKeyboardButton.CallbackData("← К списку", "gpage:$page"))
        bot.editMessageText(
            chatId = chatId,
            messageId = messageId,
            text = text,
            parseMode = ParseMode.MARKDOWN,
            replyMarkup = InlineKeyboardMarkup.create(rows),
        )
    }

    private fun inviteToGroupEvent(
        bot: Bot,
        chatId: ChatId,
        groupChatId: Long,
        text: String,
        botUsername: String,
        inviterId: Long,
    ) {
        val cleaned = text
            .replace("@$botUsername", "", ignoreCase = true)
            .replace(Regex("/group_invite(@$botUsername)?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("/ginvite(@$botUsername)?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("пригласить|добавить", RegexOption.IGNORE_CASE), "")
            .trim()
        val eventId = Regex("\\d+").find(cleaned)?.value?.toLongOrNull()
        if (eventId == null) {
            sendTemporary(bot, chatId, groupChatId, Messages.GROUP_INVITE_HELP, 120)
            return
        }
        val event = eventService.findById(eventId)
        if (event == null || event.groupChatId != groupChatId) {
            sendTemporary(bot, chatId, groupChatId, "Не нашёл мероприятие `$eventId` в этой группе. Посмотри список: /gevents", 90)
            return
        }
        if (event.ownerId != inviterId) {
            sendTemporary(bot, chatId, groupChatId, "Приглашать в это мероприятие может только организатор.", 90)
            return
        }

        val usernames = extractUsernames(cleaned, botUsername)
        if (usernames.isEmpty()) {
            sendTemporary(bot, chatId, groupChatId, Messages.GROUP_INVITE_HELP, 120)
            return
        }

        val invited = usernames.mapNotNull { usernameMention ->
            userService.findByUsername(usernameMention)?.also {
                participantService.joinIfAbsent(event.id, it.telegramId)
                runCatching {
                    bot.sendMessage(
                        chatId = ChatId.fromId(it.telegramId),
                        text = "Вас пригласили на *${event.title}* из группового чата.\nДата: ${DateUtils.format(event.startsAt)}",
                        parseMode = ParseMode.MARKDOWN,
                    )
                }
            }
        }
        val invitedNames = invited.mapNotNull { it.username }.toSet()
        val missing = usernames.filter { it.removePrefix("@") !in invitedNames }

        sendTemporary(
            bot,
            chatId,
            groupChatId,
            buildString {
                appendLine("Готово: пригласил ${invited.size} участн. в *${event.title}*.")
                if (missing.isNotEmpty()) {
                    appendLine()
                    appendLine("Не смог найти: ${missing.joinToString(" ") { "@$it" }}")
                    appendLine("Пусть они один раз напишут боту /start в личку.")
                }
            },
            180,
        )
    }

    private data class ParsedGroupEvent(
        val title: String,
        val date: String,
        val address: String,
        val description: String,
        val cost: Long,
        val visibility: EventVisibility,
        val registrationMode: EventRegistrationMode,
        val tags: Set<EventTag>,
        val usernames: List<String>,
    )

    private fun parse(text: String, botUsername: String): ParsedGroupEvent? {
        val cleaned = text
            .replace("@$botUsername", "", ignoreCase = true)
            .replace(Regex("/group_new(@$botUsername)?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("/gnew(@$botUsername)?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("создать\\s+мероприятие", RegexOption.IGNORE_CASE), "")
            .replace(Regex("запланировать\\s+мероприятие", RegexOption.IGNORE_CASE), "")
            .trim()
        val parts = cleaned.split("|").map { it.trim() }
        if (parts.size < 3) return null

        val tail = parts.drop(3).joinToString(" ")
        val usernames = extractUsernames(tail, botUsername)
        val visibility = if (
            cleaned.contains("public", ignoreCase = true) ||
            cleaned.contains("публич", ignoreCase = true) ||
            cleaned.contains("общая афиша", ignoreCase = true)
        ) {
            EventVisibility.PUBLIC
        } else {
            EventVisibility.PRIVATE
        }
        val registrationMode = if (
            cleaned.contains("invite", ignoreCase = true) ||
            cleaned.contains("по приглаш", ignoreCase = true) ||
            cleaned.contains("закрытая запись", ignoreCase = true)
        ) {
            EventRegistrationMode.INVITE_ONLY
        } else {
            EventRegistrationMode.FREE
        }
        val tags = EventTag.entries.filter { cleaned.contains("#${it.name}", ignoreCase = true) }.toSet()
        val cost = Regex("(?:cost|цена|стоимость)\\s*[:=]?\\s*(\\d+)", RegexOption.IGNORE_CASE)
            .find(cleaned)
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()
            ?: 0L
        return ParsedGroupEvent(
            title = parts[0],
            date = parts[1],
            address = parts[2],
            description = parts.getOrNull(3)?.ifBlank { "Мероприятие группы" } ?: "Мероприятие группы",
            cost = cost,
            visibility = visibility,
            registrationMode = registrationMode,
            tags = tags,
            usernames = usernames,
        )
    }

    private fun extractUsernames(text: String, botUsername: String): List<String> =
        Regex("@[A-Za-z0-9_]{5,32}")
            .findAll(text)
            .map { it.value.removePrefix("@") }
            .filter { it != botUsername }
            .distinct()
            .toList()

    private fun createdSummary(event: Event, registeredCount: Int, missing: List<String>): String {
        val visibility = if (event.visibility == EventVisibility.PRIVATE) "только для этой группы" else "публичное"
        val registration = if (event.registrationMode == EventRegistrationMode.FREE) "свободная запись" else "по приглашению"
        return buildString {
            appendLine("✅ Создал мероприятие группы: *${event.title}*")
            appendLine("Видимость: $visibility")
            appendLine("Запись: $registration")
            appendLine("Дата: ${DateUtils.format(event.startsAt)}")
            appendLine("Адрес: ${event.address}")
            appendLine("Уже приглашено: $registeredCount")
            if (missing.isNotEmpty()) {
                appendLine()
                appendLine("Не смог записать: ${missing.joinToString(" ") { "@$it" }}")
                appendLine("Эти люди должны один раз написать боту /start, потом их можно будет приглашать.")
            }
        }
    }

    private fun sendTemporary(bot: Bot, chatId: ChatId, chatIdValue: Long, text: String, ttlSeconds: Long) {
        val result = bot.sendMessage(
            chatId = chatId,
            text = text,
            parseMode = ParseMode.MARKDOWN,
        )
        MessageCleaner.deleteLater(bot, chatIdValue, result, ttlSeconds)
    }

    private companion object {
        const val GROUP_PAGE_SIZE = 5
    }
}
