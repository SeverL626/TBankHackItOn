package com.meventus.bot.handlers

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
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
            val username = botUsername.removePrefix("@")
            val mentionsBot = text.contains("@$username", ignoreCase = true)
            val startsCommand = text.startsWith("/group_new") || text.startsWith("/gnew")
            if (!mentionsBot && !startsCommand) return@text
            if (!text.contains("созд", ignoreCase = true) && !startsCommand) return@text

            userService.registerIfAbsent(from.id, from.username, from.firstName)

            val parsed = parse(text, username)
            if (parsed == null) {
                bot.sendMessage(
                    chatId = ChatId.fromId(message.chat.id),
                    text = GROUP_HELP,
                    parseMode = ParseMode.MARKDOWN,
                )
                return@text
            }

            val startsAt = runCatching { DateUtils.parse(parsed.date) }.getOrNull()
            if (startsAt == null) {
                bot.sendMessage(ChatId.fromId(message.chat.id), "Не понял дату. Формат: `25.05.2026 18:00`", parseMode = ParseMode.MARKDOWN)
                return@text
            }

            val event = eventService.create(
                ownerId = from.id,
                title = parsed.title,
                shortDescription = "Создано из группы",
                description = parsed.description.ifBlank { "Групповое мероприятие" },
                address = parsed.address,
                startsAt = startsAt,
                cost = parsed.cost,
                photoFileId = null,
                tags = parsed.tags,
                visibility = parsed.visibility,
                groupChatId = message.chat.id,
            )

            val registered = parsed.usernames.mapNotNull { usernameMention ->
                userService.findByUsername(usernameMention)?.also {
                    participantService.joinIfAbsent(event.id, it.telegramId)
                    runCatching {
                        bot.sendMessage(
                            chatId = ChatId.fromId(it.telegramId),
                            text = "Вас записали на *${event.title}* из группового чата.\nДата: ${DateUtils.format(event.startsAt)}",
                            parseMode = ParseMode.MARKDOWN,
                        )
                    }
                }
            }
            val registeredNames = registered.mapNotNull { it.username }.toSet()
            val missing = parsed.usernames.filter { it.removePrefix("@") !in registeredNames }

            val visibilityText = if (event.visibility == EventVisibility.PRIVATE) "приватное" else "публичное"
            val reply = buildString {
                appendLine("✅ Мероприятие создано: *${event.title}*")
                appendLine("Тип: $visibilityText")
                appendLine("Дата: ${DateUtils.format(event.startsAt)}")
                appendLine("Адрес: ${event.address}")
                appendLine("Участников записано: ${registered.size}")
                if (missing.isNotEmpty()) {
                    appendLine()
                    appendLine("Не нашёл в базе: ${missing.joinToString(" ")}")
                    appendLine("Попросите их написать боту /start, затем повторите запись вручную.")
                }
            }
            bot.sendMessage(ChatId.fromId(message.chat.id), reply, parseMode = ParseMode.MARKDOWN)
        }
    }

    private data class ParsedGroupEvent(
        val title: String,
        val date: String,
        val address: String,
        val description: String,
        val cost: Long,
        val visibility: EventVisibility,
        val tags: Set<EventTag>,
        val usernames: List<String>,
    )

    private fun parse(text: String, botUsername: String): ParsedGroupEvent? {
        val cleaned = text
            .replace("@$botUsername", "", ignoreCase = true)
            .replace("/group_new", "", ignoreCase = true)
            .replace("/gnew", "", ignoreCase = true)
            .replace(Regex("создать\\s+мероприятие", RegexOption.IGNORE_CASE), "")
            .trim()
        val parts = cleaned.split("|").map { it.trim() }
        if (parts.size < 4) return null

        val usernames = Regex("@[A-Za-z0-9_]{5,32}")
            .findAll(parts.drop(4).joinToString(" "))
            .map { it.value.removePrefix("@") }
            .filter { it != botUsername }
            .distinct()
            .toList()
        val visibility = if (cleaned.contains("private", ignoreCase = true) || cleaned.contains("приват", ignoreCase = true)) {
            EventVisibility.PRIVATE
        } else {
            EventVisibility.PUBLIC
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
            tags = tags,
            usernames = usernames,
        )
    }

    private companion object {
        const val GROUP_HELP = """
Формат группового создания:
`@meventur_bot создать мероприятие | Название | 25.05.2026 18:00 | Адрес | Описание | @user1 @user2 | private`

`private` можно заменить на `public`. Теги: `#IT #SPORT #OUTDOORS #INDOORS`.
Упомянутые пользователи должны быть известны боту: им нужно хотя бы раз написать /start.
"""
    }
}
