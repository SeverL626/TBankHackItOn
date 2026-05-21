package com.meventus.bot.handlers

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.meventus.bot.keyboards.TagKeyboard
import com.meventus.bot.states.StateStorage
import com.meventus.bot.states.UserState
import com.meventus.domain.service.EventService
import com.meventus.util.DateUtils

class EventCreateHandler(
    private val eventService: EventService,
    private val stateStorage: StateStorage,
) {
    fun register(dispatcher: Dispatcher) {

        // ── Text steps ────────────────────────────────────────────────────────
        dispatcher.text {
            val userId = message.from?.id ?: return@text
            val text = message.text ?: return@text
            val chatId = ChatId.fromId(message.chat.id)

            when (val state = stateStorage.get(userId)) {
                is UserState.Idle -> return@text

                is UserState.AwaitingEventTitle -> {
                    stateStorage.set(userId, UserState.AwaitingEventShortDesc(text))
                    bot.sendMessage(chatId, "Шаг 2/7 — Введите *краткое описание* (1–2 строки, покажется в списке):", parseMode = ParseMode.MARKDOWN)
                }

                is UserState.AwaitingEventShortDesc -> {
                    stateStorage.set(userId, UserState.AwaitingEventDescription(state.title, text))
                    bot.sendMessage(chatId, "Шаг 3/7 — Введите *полное описание*:", parseMode = ParseMode.MARKDOWN)
                }

                is UserState.AwaitingEventDescription -> {
                    stateStorage.set(userId, UserState.AwaitingEventAddress(state.title, state.shortDesc, text))
                    bot.sendMessage(chatId, "Шаг 4/7 — Введите *адрес* проведения:", parseMode = ParseMode.MARKDOWN)
                }

                is UserState.AwaitingEventAddress -> {
                    stateStorage.set(userId, UserState.AwaitingEventDate(state.title, state.shortDesc, state.description, text))
                    bot.sendMessage(chatId, "Шаг 5/7 — Введите *дату и время* в формате `ДД.ММ.ГГГГ ЧЧ:ММ`\nНапример: `25.05.2026 18:00`", parseMode = ParseMode.MARKDOWN)
                }

                is UserState.AwaitingEventDate -> {
                    val parsed = runCatching { DateUtils.parse(text) }.getOrNull()
                    if (parsed == null) {
                        bot.sendMessage(chatId, "Неверный формат. Введите дату так: `25.05.2026 18:00`", parseMode = ParseMode.MARKDOWN)
                        return@text
                    }
                    stateStorage.set(userId, UserState.AwaitingEventCost(state.title, state.shortDesc, state.description, state.address, text))
                    bot.sendMessage(chatId, "Шаг 6/7 — Введите *стоимость* участия в рублях (0 — бесплатно):", parseMode = ParseMode.MARKDOWN)
                }

                is UserState.AwaitingEventCost -> {
                    val cost = text.toLongOrNull()
                    if (cost == null || cost < 0) {
                        bot.sendMessage(chatId, "Введите целое число ≥ 0")
                        return@text
                    }
                    stateStorage.set(userId, UserState.AwaitingEventPhoto(state.title, state.shortDesc, state.description, state.address, state.startsAt, cost))
                    bot.sendMessage(chatId, "Шаг 7/7 — Отправьте *фото* мероприятия или напишите `пропустить`:", parseMode = ParseMode.MARKDOWN)
                }

                is UserState.AwaitingEventPhoto -> {
                    if (text.trim().lowercase() == "пропустить") {
                        showTagSelection(bot, chatId, userId, state, photoFileId = null)
                    } else {
                        bot.sendMessage(chatId, "Отправьте фото или напишите `пропустить`", parseMode = ParseMode.MARKDOWN)
                    }
                }

                is UserState.AwaitingEventTags -> return@text
            }
        }

        // ── Photo step ────────────────────────────────────────────────────────
        dispatcher.message {
            val userId = message.from?.id ?: return@message
            val photo = message.photo?.lastOrNull() ?: return@message
            val state = stateStorage.get(userId) as? UserState.AwaitingEventPhoto ?: return@message
            val chatId = ChatId.fromId(message.chat.id)
            showTagSelection(bot, chatId, userId, state, photoFileId = photo.fileId)
        }

        // ── Tag toggle callbacks ───────────────────────────────────────────────
        dispatcher.callbackQuery {
            val data = callbackQuery.data ?: return@callbackQuery
            val userId = callbackQuery.from.id
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val messageId = callbackQuery.message?.messageId ?: return@callbackQuery

            when {
                data.startsWith("ctag:") -> {
                    val state = stateStorage.get(userId) as? UserState.AwaitingEventTags ?: return@callbackQuery
                    val newTags = TagKeyboard.parseBitmask(data.removePrefix("ctag:"))
                    stateStorage.set(userId, state.copy(selectedTags = newTags))
                    bot.answerCallbackQuery(callbackQuery.id)
                    bot.editMessageReplyMarkup(
                        chatId = ChatId.fromId(chatId),
                        messageId = messageId,
                        replyMarkup = TagKeyboard.forCreate(newTags),
                    )
                }

                data.startsWith("ctag_done:") -> {
                    val state = stateStorage.get(userId) as? UserState.AwaitingEventTags ?: return@callbackQuery
                    val tags = TagKeyboard.parseBitmask(data.removePrefix("ctag_done:"))
                    val startsAt = runCatching { DateUtils.parse(state.startsAt) }.getOrNull()
                    if (startsAt == null) {
                        bot.answerCallbackQuery(callbackQuery.id, "Ошибка даты, начните заново /new")
                        stateStorage.clear(userId)
                        return@callbackQuery
                    }
                    val event = eventService.create(
                        ownerId = userId,
                        title = state.title,
                        shortDescription = state.shortDesc,
                        description = state.description,
                        address = state.address,
                        startsAt = startsAt,
                        cost = state.cost,
                        photoFileId = state.photoFileId,
                        tags = tags,
                    )
                    stateStorage.clear(userId)
                    bot.answerCallbackQuery(callbackQuery.id, "Мероприятие создано!")
                    bot.editMessageText(
                        chatId = ChatId.fromId(chatId),
                        messageId = messageId,
                        text = "✅ *${event.title}* создано!\n\nID: `${event.id}`\nНайди его через /events",
                        parseMode = ParseMode.MARKDOWN,
                    )
                }
            }
        }
    }

    private fun showTagSelection(
        bot: com.github.kotlintelegrambot.Bot,
        chatId: ChatId,
        userId: Long,
        state: UserState.AwaitingEventPhoto,
        photoFileId: String?,
    ) {
        stateStorage.set(
            userId,
            UserState.AwaitingEventTags(
                title = state.title,
                shortDesc = state.shortDesc,
                description = state.description,
                address = state.address,
                startsAt = state.startsAt,
                cost = state.cost,
                photoFileId = photoFileId,
            ),
        )
        bot.sendMessage(
            chatId = chatId,
            text = "Выберите *теги* мероприятия (можно несколько), затем нажмите *Готово*:",
            parseMode = ParseMode.MARKDOWN,
            replyMarkup = TagKeyboard.forCreate(emptySet()),
        )
    }
}
