package com.meventus.bot.handlers

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
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
                    bot.sendMessage(ChatId.fromId(chatId), GROUP_HELP, parseMode = ParseMode.MARKDOWN)
                }

                command in setOf("/gevents", "/group_events") ||
                    (mentionsBot && ("мероприят" in normalized || "событ" in normalized || "афиша" in normalized)) -> {
                    sendGroupEvents(bot, ChatId.fromId(chatId), chatId)
                }

                command in setOf("/gnew", "/group_new") ||
                    (mentionsBot && ("созд" in normalized || "заплан" in normalized)) -> {
                    userService.registerIfAbsent(from.id, from.username, from.firstName)
                    createGroupEvent(bot, ChatId.fromId(chatId), chatId, text, username, from.id)
                }
            }
        }
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
            bot.sendMessage(chatId, GROUP_CREATE_HELP, parseMode = ParseMode.MARKDOWN)
            return
        }

        val startsAt = runCatching { DateUtils.parse(parsed.date) }.getOrNull()
        if (startsAt == null) {
            bot.sendMessage(chatId, "Не понял дату. Нужен формат: `25.05.2026 18:00`", parseMode = ParseMode.MARKDOWN)
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

        bot.sendMessage(
            chatId = chatId,
            text = createdSummary(event, registered.size, missing),
            parseMode = ParseMode.MARKDOWN,
        )
        sendGroupEventCard(bot, chatId, event)
    }

    private fun sendGroupEvents(bot: Bot, chatId: ChatId, groupChatId: Long) {
        val events = eventService.listByGroup(groupChatId)
        if (events.isEmpty()) {
            bot.sendMessage(
                chatId = chatId,
                text = "В этой группе пока нет ближайших мероприятий.\n\nСоздать: /gnew Название | 25.05.2026 18:00 | Место | Описание | free private",
                parseMode = ParseMode.MARKDOWN,
            )
            return
        }

        bot.sendMessage(
            chatId = chatId,
            text = "Мероприятия *этой группы*. Здесь не показываю общую афишу бота.",
            parseMode = ParseMode.MARKDOWN,
        )
        events.forEach { sendGroupEventCard(bot, chatId, it) }
    }

    private fun sendGroupEventCard(bot: Bot, chatId: ChatId, event: Event) {
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
            appendLine("$visibility · $registration")
            if (tags.isNotBlank()) appendLine(tags)
            appendLine()
            appendLine(event.description)
            appendLine()
            appendLine("📅 ${DateUtils.format(event.startsAt)}")
            appendLine("📍 ${event.address}")
            appendLine("👥 $participants участн.")
        }
        val rows = if (event.registrationMode == EventRegistrationMode.FREE) {
            listOf(
                listOf(InlineKeyboardButton.CallbackData("✅ Участвовать", "ejoin:${event.id}")),
                listOf(InlineKeyboardButton.CallbackData("🔍 Подробнее", "edetail:${event.id}")),
            )
        } else {
            listOf(listOf(InlineKeyboardButton.CallbackData("🔍 Подробнее", "edetail:${event.id}")))
        }
        bot.sendMessage(
            chatId = chatId,
            text = text,
            parseMode = ParseMode.MARKDOWN,
            replyMarkup = InlineKeyboardMarkup.create(rows),
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
        if (parts.size < 4) return null

        val tail = parts.drop(4).joinToString(" ")
        val usernames = Regex("@[A-Za-z0-9_]{5,32}")
            .findAll(tail)
            .map { it.value.removePrefix("@") }
            .filter { it != botUsername }
            .distinct()
            .toList()
        val visibility = if (
            cleaned.contains("private", ignoreCase = true) ||
            cleaned.contains("приват", ignoreCase = true) ||
            cleaned.contains("только группа", ignoreCase = true)
        ) {
            EventVisibility.PRIVATE
        } else {
            EventVisibility.PUBLIC
        }
        val registrationMode = if (
            cleaned.contains("invite", ignoreCase = true) ||
            cleaned.contains("по приглаш", ignoreCase = true) ||
            cleaned.contains("закрытая запись", ignoreCase = true) ||
            (usernames.isNotEmpty() && !cleaned.contains("free", ignoreCase = true) && !cleaned.contains("свобод", ignoreCase = true))
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
            description = parts[3],
            cost = cost,
            visibility = visibility,
            registrationMode = registrationMode,
            tags = tags,
            usernames = usernames,
        )
    }

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

    private companion object {
        const val GROUP_HELP = """
*Meventus для этой группы*

Я веду мероприятия именно этого чата: список, запись и приглашения команды.

Команды в группе:
/gevents — мероприятия этой группы
/gnew — создать мероприятие группы
/ghelp — помощь по группе

Режимы:
*free* / `свободная` — любой в группе нажимает "Участвовать"
*invite* / `по приглашению` — записываю только упомянутых людей

Видимость:
*private* / `приват` — только эта группа и участники
*public* — попадёт ещё и в общую афишу
"""

        const val GROUP_CREATE_HELP = """
*Создание мероприятия группы*

Свободная запись, только для группы:
`/gnew Демо-день | 25.05.2026 18:00 | офис | показываем проекты | free private #IT`

По приглашению:
`/gnew Созвон | 25.05.2026 18:00 | онлайн | синк команды | invite private @user1 @user2`

Поля через `|`: название, дата, место, описание, настройки.
"""
    }
}
