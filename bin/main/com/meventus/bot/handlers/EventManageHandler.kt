package com.meventus.bot.handlers

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.meventus.bot.states.StateStorage
import com.meventus.bot.states.UserState
import com.meventus.domain.model.Event
import com.meventus.domain.model.EventRegistrationMode
import com.meventus.domain.model.EventVisibility
import com.meventus.domain.service.CustomReminderService
import com.meventus.domain.service.EventService
import com.meventus.domain.service.ParticipantService
import com.meventus.domain.service.UserService
import com.meventus.util.DateUtils

class EventManageHandler(
    private val eventService: EventService,
    private val participantService: ParticipantService,
    private val userService: UserService,
    private val customReminderService: CustomReminderService,
    private val stateStorage: StateStorage,
) {
    fun register(dispatcher: Dispatcher) {
        dispatcher.callbackQuery {
            val data = callbackQuery.data ?: return@callbackQuery
            val userId = callbackQuery.from.id
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val messageId = callbackQuery.message?.messageId ?: return@callbackQuery

            when {
                data.startsWith("manage:") -> {
                    val eventId = data.removePrefix("manage:").toLongOrNull() ?: return@callbackQuery
                    val event = ownedEvent(eventId, userId) ?: run {
                        bot.answerCallbackQuery(callbackQuery.id, "Нет доступа")
                        return@callbackQuery
                    }
                    bot.answerCallbackQuery(callbackQuery.id)
                    showManager(bot, ChatId.fromId(chatId), messageId, event)
                }

                data.startsWith("eedit:") -> {
                    val parts = data.removePrefix("eedit:").split(":")
                    val eventId = parts.getOrNull(0)?.toLongOrNull() ?: return@callbackQuery
                    val field = parts.getOrNull(1) ?: return@callbackQuery
                    val event = ownedEvent(eventId, userId) ?: run {
                        bot.answerCallbackQuery(callbackQuery.id, "Нет доступа")
                        return@callbackQuery
                    }
                    stateStorage.set(userId, UserState.AwaitingEventEdit(event.id, field))
                    bot.answerCallbackQuery(callbackQuery.id)
                    bot.sendMessage(
                        chatId = ChatId.fromId(chatId),
                        text = editPrompt(event, field),
                        parseMode = ParseMode.MARKDOWN,
                    )
                }

                data.startsWith("etogglevis:") -> {
                    val eventId = data.removePrefix("etogglevis:").toLongOrNull() ?: return@callbackQuery
                    val event = ownedEvent(eventId, userId) ?: run {
                        bot.answerCallbackQuery(callbackQuery.id, "Нет доступа")
                        return@callbackQuery
                    }
                    val next = if (event.visibility == EventVisibility.PUBLIC) EventVisibility.PRIVATE else EventVisibility.PUBLIC
                    val updated = saveEvent(event.copy(visibility = next)) ?: return@callbackQuery
                    notifyGroup(bot, updated, "Настройки мероприятия обновлены: ${visibilityText(updated)}.")
                    bot.answerCallbackQuery(callbackQuery.id, "Видимость обновлена")
                    showManager(bot, ChatId.fromId(chatId), messageId, updated)
                }

                data.startsWith("etogglereg:") -> {
                    val eventId = data.removePrefix("etogglereg:").toLongOrNull() ?: return@callbackQuery
                    val event = ownedEvent(eventId, userId) ?: run {
                        bot.answerCallbackQuery(callbackQuery.id, "Нет доступа")
                        return@callbackQuery
                    }
                    val next = if (event.registrationMode == EventRegistrationMode.FREE) {
                        EventRegistrationMode.INVITE_ONLY
                    } else {
                        EventRegistrationMode.FREE
                    }
                    val updated = saveEvent(event.copy(registrationMode = next)) ?: return@callbackQuery
                    notifyGroup(bot, updated, "Настройки записи обновлены: ${registrationText(updated)}.")
                    bot.answerCallbackQuery(callbackQuery.id, "Режим записи обновлён")
                    showManager(bot, ChatId.fromId(chatId), messageId, updated)
                }

                data.startsWith("eparticipants:") -> {
                    val eventId = data.removePrefix("eparticipants:").toLongOrNull() ?: return@callbackQuery
                    val event = ownedEvent(eventId, userId) ?: run {
                        bot.answerCallbackQuery(callbackQuery.id, "Нет доступа")
                        return@callbackQuery
                    }
                    bot.answerCallbackQuery(callbackQuery.id)
                    showParticipants(bot, ChatId.fromId(chatId), messageId, event)
                }

                data.startsWith("ereminder:") -> {
                    val eventId = data.removePrefix("ereminder:").toLongOrNull() ?: return@callbackQuery
                    val event = ownedEvent(eventId, userId) ?: run {
                        bot.answerCallbackQuery(callbackQuery.id, "Нет доступа")
                        return@callbackQuery
                    }
                    stateStorage.set(userId, UserState.AwaitingCustomReminderTime(event.id))
                    bot.answerCallbackQuery(callbackQuery.id)
                    bot.sendMessage(
                        chatId = ChatId.fromId(chatId),
                        text = """
                            ⏰ *Кастомное уведомление*

                            Напиши, за сколько до начала отправить уведомление.

                            Примеры:
                            `1ч`
                            `30м`
                            `5с`
                            `01:30:00`

                            Для группового события уведомление уйдёт в группу. Для личного — участникам и организатору.
                        """.trimIndent(),
                        parseMode = ParseMode.MARKDOWN,
                    )
                }

                data.startsWith("eremove:") -> {
                    val parts = data.removePrefix("eremove:").split(":")
                    val eventId = parts.getOrNull(0)?.toLongOrNull() ?: return@callbackQuery
                    val participantId = parts.getOrNull(1)?.toLongOrNull() ?: return@callbackQuery
                    val event = ownedEvent(eventId, userId) ?: run {
                        bot.answerCallbackQuery(callbackQuery.id, "Нет доступа")
                        return@callbackQuery
                    }
                    participantService.leave(eventId, participantId)
                    bot.answerCallbackQuery(callbackQuery.id, "Участник удалён")
                    runCatching {
                        bot.sendMessage(
                            chatId = ChatId.fromId(participantId),
                            text = "Вас удалили из участников мероприятия *${event.title}*.",
                            parseMode = ParseMode.MARKDOWN,
                        )
                    }
                    notifyGroup(bot, event, "Из участников мероприятия *${event.title}* удалён участник.")
                    showParticipants(bot, ChatId.fromId(chatId), messageId, event)
                }

                data.startsWith("ecancel:") -> {
                    val eventId = data.removePrefix("ecancel:").toLongOrNull() ?: return@callbackQuery
                    val event = ownedEvent(eventId, userId) ?: run {
                        bot.answerCallbackQuery(callbackQuery.id, "Нет доступа")
                        return@callbackQuery
                    }
                    eventService.cancel(eventId)
                    bot.answerCallbackQuery(callbackQuery.id, "Мероприятие снято")
                    notifyGroup(bot, event, "Мероприятие *${event.title}* снято с публикации.")
                    bot.editMessageText(
                        chatId = ChatId.fromId(chatId),
                        messageId = messageId,
                        text = "🗑 *${event.title}* снято с публикации.",
                        parseMode = ParseMode.MARKDOWN,
                    )
                }

                data.startsWith("edeleteask:") -> {
                    val eventId = data.removePrefix("edeleteask:").toLongOrNull() ?: return@callbackQuery
                    val event = ownedEvent(eventId, userId) ?: run {
                        bot.answerCallbackQuery(callbackQuery.id, "Нет доступа")
                        return@callbackQuery
                    }
                    bot.answerCallbackQuery(callbackQuery.id)
                    bot.editMessageText(
                        chatId = ChatId.fromId(chatId),
                        messageId = messageId,
                        text = "Удалить *${event.title}* навсегда?\n\nЭто удалит мероприятие, участников и теги. Отменить действие будет нельзя.",
                        parseMode = ParseMode.MARKDOWN,
                        replyMarkup = InlineKeyboardMarkup.create(
                            listOf(
                                listOf(InlineKeyboardButton.CallbackData("Да, удалить", "edelete:${event.id}")),
                                listOf(InlineKeyboardButton.CallbackData("← Оставить", "manage:${event.id}")),
                            ),
                        ),
                    )
                }

                data.startsWith("edelete:") -> {
                    val eventId = data.removePrefix("edelete:").toLongOrNull() ?: return@callbackQuery
                    val event = ownedEvent(eventId, userId) ?: run {
                        bot.answerCallbackQuery(callbackQuery.id, "Нет доступа")
                        return@callbackQuery
                    }
                    val groupChatId = event.groupChatId
                    val deleted = eventService.delete(eventId, userId)
                    if (!deleted) {
                        bot.answerCallbackQuery(callbackQuery.id, "Не удалось удалить")
                        return@callbackQuery
                    }
                    bot.answerCallbackQuery(callbackQuery.id, "Мероприятие удалено")
                    if (groupChatId != null) {
                        runCatching {
                            bot.sendMessage(
                                chatId = ChatId.fromId(groupChatId),
                                text = "Мероприятие *${event.title}* удалено.",
                                parseMode = ParseMode.MARKDOWN,
                            )
                        }
                    }
                    bot.editMessageText(
                        chatId = ChatId.fromId(chatId),
                        messageId = messageId,
                        text = "Удалено: *${event.title}*.",
                        parseMode = ParseMode.MARKDOWN,
                    )
                }
            }
        }

        dispatcher.text {
            val userId = message.from?.id ?: return@text
            val state = stateStorage.get(userId)
            if (state !is UserState.AwaitingEventEdit &&
                state !is UserState.AwaitingCustomReminderTime &&
                state !is UserState.AwaitingCustomReminderMessage
            ) {
                return@text
            }
            val text = message.text?.trim() ?: return@text
            val chatId = ChatId.fromId(message.chat.id)

            if (text == "/cancel" || text.equals("отмена", ignoreCase = true)) {
                stateStorage.clear(userId)
                bot.sendMessage(chatId, "Действие отменено.")
                return@text
            }

            if (state is UserState.AwaitingCustomReminderTime) {
                val event = ownedEvent(state.eventId, userId)
                if (event == null) {
                    stateStorage.clear(userId)
                    bot.sendMessage(chatId, "Мероприятие не найдено или у вас нет доступа.")
                    return@text
                }
                val secondsBefore = parseReminderOffset(text)
                if (secondsBefore == null || secondsBefore < 0) {
                    bot.sendMessage(
                        chatId = chatId,
                        text = "Не понял время. Напиши, например: `1ч`, `30м`, `5с` или `01:30:00`.",
                        parseMode = ParseMode.MARKDOWN,
                    )
                    return@text
                }
                stateStorage.set(userId, UserState.AwaitingCustomReminderMessage(state.eventId, secondsBefore))
                bot.sendMessage(
                    chatId = chatId,
                    text = "Теперь напиши текст уведомления.\n\nНапример: `Через час начинаем, не забудьте ноутбук.`",
                    parseMode = ParseMode.MARKDOWN,
                )
                return@text
            }

            if (state is UserState.AwaitingCustomReminderMessage) {
                val event = ownedEvent(state.eventId, userId)
                if (event == null) {
                    stateStorage.clear(userId)
                    bot.sendMessage(chatId, "Мероприятие не найдено или у вас нет доступа.")
                    return@text
                }
                customReminderService.create(event.id, state.secondsBefore, text)
                stateStorage.clear(userId)
                bot.sendMessage(
                    chatId = chatId,
                    text = "✅ Уведомление добавлено: за ${formatOffset(state.secondsBefore)} до *${event.title}*.",
                    parseMode = ParseMode.MARKDOWN,
                )
                return@text
            }

            val editState = state as UserState.AwaitingEventEdit
            val event = ownedEvent(editState.eventId, userId)
            if (event == null) {
                stateStorage.clear(userId)
                bot.sendMessage(chatId, "Мероприятие не найдено или у вас нет доступа.")
                return@text
            }

            val updated = when (editState.field) {
                "title" -> saveEvent(event.copy(title = text))
                "short" -> saveEvent(event.copy(shortDescription = text))
                "desc" -> saveEvent(event.copy(description = text))
                "address" -> saveEvent(event.copy(address = text))
                "date" -> {
                    val startsAt = runCatching { DateUtils.parse(text) }.getOrNull()
                    if (startsAt == null) {
                        bot.sendMessage(chatId, "Не понял дату. Формат: `ДД.ММ.ГГГГ ЧЧ:ММ`", parseMode = ParseMode.MARKDOWN)
                        return@text
                    }
                    saveEvent(event.copy(startsAt = startsAt))
                }
                "cost" -> {
                    val cost = text.toLongOrNull()
                    if (cost == null || cost < 0) {
                        bot.sendMessage(chatId, "Введите целое число ≥ 0.")
                        return@text
                    }
                    saveEvent(event.copy(cost = cost))
                }
                else -> null
            }

            stateStorage.clear(userId)
            if (updated == null) {
                bot.sendMessage(chatId, "Не удалось обновить мероприятие.")
                return@text
            }

            notifyGroup(bot, updated, "Мероприятие *${updated.title}* обновлено.")
            bot.sendMessage(
                chatId = chatId,
                text = "✅ Обновил *${updated.title}*.\n\nОткрыть управление: /my",
                parseMode = ParseMode.MARKDOWN,
            )
        }
    }

    private fun ownedEvent(eventId: Long, userId: Long): Event? =
        eventService.findById(eventId)?.takeIf { it.ownerId == userId }

    private fun saveEvent(event: Event): Event? =
        eventService.update(
            eventId = event.id,
            ownerId = event.ownerId,
            title = event.title,
            shortDescription = event.shortDescription,
            description = event.description,
            address = event.address,
            startsAt = event.startsAt,
            cost = event.cost,
            tags = event.tags,
            paymentType = event.paymentType,
            sbpPhone = event.sbpPhone,
            sbpName = event.sbpName,
            visibility = event.visibility,
            registrationMode = event.registrationMode,
        )

    private fun showManager(bot: Bot, chatId: ChatId, messageId: Long, event: Event) {
        val participants = participantService.listByEvent(event.id).size
        val text = """
            ⚙️ *${event.title}*

            ${visibilityText(event)}
            ${registrationText(event)}
            📅 ${DateUtils.format(event.startsAt)}
            📍 ${event.address}
            👥 Участников: $participants

            Всё это можно делать и в экспериментальном мини-приложении, но ниже есть чатовые действия.
        """.trimIndent()
        bot.editMessageText(
            chatId = chatId,
            messageId = messageId,
            text = text,
            parseMode = ParseMode.MARKDOWN,
            replyMarkup = InlineKeyboardMarkup.create(
                listOf(
                    listOf(
                        InlineKeyboardButton.CallbackData("✏️ Название", "eedit:${event.id}:title"),
                        InlineKeyboardButton.CallbackData("📝 Описание", "eedit:${event.id}:desc"),
                    ),
                    listOf(
                        InlineKeyboardButton.CallbackData("📍 Место", "eedit:${event.id}:address"),
                        InlineKeyboardButton.CallbackData("📅 Дата", "eedit:${event.id}:date"),
                    ),
                    listOf(
                        InlineKeyboardButton.CallbackData("💰 Цена", "eedit:${event.id}:cost"),
                        InlineKeyboardButton.CallbackData("👥 Участники", "eparticipants:${event.id}"),
                    ),
                    listOf(
                        InlineKeyboardButton.CallbackData("⏰ Напоминание", "ereminder:${event.id}"),
                    ),
                    listOf(
                        InlineKeyboardButton.CallbackData("🌍/🔒 Видимость", "etogglevis:${event.id}"),
                        InlineKeyboardButton.CallbackData("✅/🔐 Запись", "etogglereg:${event.id}"),
                    ),
                    listOf(
                        InlineKeyboardButton.CallbackData("📢 Уведомить", "bcast:${event.id}"),
                        InlineKeyboardButton.CallbackData("🗑 Снять", "ecancel:${event.id}"),
                    ),
                    listOf(
                        InlineKeyboardButton.CallbackData("Удалить навсегда", "edeleteask:${event.id}"),
                    ),
                ),
            ),
        )
    }

    private fun showParticipants(bot: Bot, chatId: ChatId, messageId: Long, event: Event) {
        val participants = participantService.listByEvent(event.id)
        if (participants.isEmpty()) {
            bot.editMessageText(
                chatId = chatId,
                messageId = messageId,
                text = "У *${event.title}* пока нет участников.",
                parseMode = ParseMode.MARKDOWN,
                replyMarkup = InlineKeyboardMarkup.create(
                    listOf(InlineKeyboardButton.CallbackData("← Управление", "manage:${event.id}")),
                ),
            )
            return
        }

        val rows = participants.map { participant ->
            val user = userService.findByTelegramId(participant.userId)
            val name = user?.username?.let { "@$it" } ?: user?.firstName ?: participant.userId.toString()
            listOf(InlineKeyboardButton.CallbackData("Удалить $name", "eremove:${event.id}:${participant.userId}"))
        }.toMutableList()
        rows += listOf(InlineKeyboardButton.CallbackData("← Управление", "manage:${event.id}"))
        bot.editMessageText(
            chatId = chatId,
            messageId = messageId,
            text = "Участники *${event.title}*:",
            parseMode = ParseMode.MARKDOWN,
            replyMarkup = InlineKeyboardMarkup.create(rows),
        )
    }

    private fun editPrompt(event: Event, field: String): String = when (field) {
        "title" -> "Новое название для *${event.title}*:"
        "short" -> "Новое краткое описание для карточки:"
        "desc" -> "Новое полное описание:"
        "address" -> "Новое место или адрес:"
        "date" -> "Новая дата. Формат: `ДД.ММ.ГГГГ ЧЧ:ММ`"
        "cost" -> "Новая стоимость в рублях. `0` — бесплатно."
        else -> "Введите новое значение:"
    }

    private fun visibilityText(event: Event): String =
        if (event.visibility == EventVisibility.PRIVATE) "🔒 Приватное" else "🌍 Публичное"

    private fun registrationText(event: Event): String =
        if (event.registrationMode == EventRegistrationMode.INVITE_ONLY) "🔐 По приглашению" else "✅ Свободная запись"

    private fun notifyGroup(bot: Bot, event: Event, text: String) {
        val groupChatId = event.groupChatId ?: return
        runCatching {
            bot.sendMessage(
                chatId = ChatId.fromId(groupChatId),
                text = text,
                parseMode = ParseMode.MARKDOWN,
            )
        }
    }

    private fun parseReminderOffset(raw: String): Long? {
        val text = raw.trim().lowercase()
        val hms = Regex("""^(\d{1,2}):(\d{2})(?::(\d{2}))?$""").matchEntire(text)
        if (hms != null) {
            val hours = hms.groupValues[1].toLong()
            val minutes = hms.groupValues[2].toLong()
            val seconds = hms.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }?.toLong() ?: 0
            return hours * 3600 + minutes * 60 + seconds
        }
        val compact = Regex("""^(\d+)\s*(с|сек|секунд|m|м|мин|минут|h|ч|час|часа|часов)$""").matchEntire(text)
            ?: return text.toLongOrNull()?.let { it * 60 }
        val amount = compact.groupValues[1].toLong()
        return when (compact.groupValues[2]) {
            "с", "сек", "секунд" -> amount
            "m", "м", "мин", "минут" -> amount * 60
            "h", "ч", "час", "часа", "часов" -> amount * 3600
            else -> null
        }
    }

    private fun formatOffset(seconds: Long): String = when {
        seconds % 3600 == 0L -> "${seconds / 3600} ч"
        seconds % 60 == 0L -> "${seconds / 60} мин"
        else -> "$seconds сек"
    }
}
