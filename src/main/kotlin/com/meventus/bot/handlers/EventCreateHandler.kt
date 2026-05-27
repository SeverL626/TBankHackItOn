package com.meventus.bot.handlers

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.meventus.bot.keyboards.TagKeyboard
import com.meventus.bot.states.StateStorage
import com.meventus.bot.states.UserState
import com.meventus.domain.model.PaymentType
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

            val normalized = text.trim().lowercase()

            // All slash-commands except /cancel must not be treated as wizard input
            if (normalized.startsWith("/") && normalized != "/cancel") return@text

            // Keyboard menu buttons also trigger text{} — ignore them
            if (normalized in setOf(
                    "📋 мероприятия",
                    "🔎 найти",
                    "⭐ мои события",
                    "👤 мои",
                    "➕ создать событие",
                    "➕ создать",
                    "📢 рассылка",
                    "📊 статистика",
                    "🌐 mini app",
                    "❓ помощь",
                )
            ) return@text

            if (normalized in listOf("отмена", "cancel", "/cancel")) {
                val state = stateStorage.get(userId)
                if (state !is UserState.Idle) {
                    stateStorage.clear(userId)
                    bot.sendMessage(chatId, "Создание мероприятия отменено.")
                }
                return@text
            }

            when (val state = stateStorage.get(userId)) {
                is UserState.Idle -> return@text

                is UserState.AwaitingEventTitle -> {
                    stateStorage.set(userId, UserState.AwaitingEventShortDesc(text, state.visibility))
                    bot.sendMessage(chatId, "Шаг 2/8 — введи *краткое описание* для карточки.\nНапример: `Встречаемся обсудить проекты и познакомиться`", parseMode = ParseMode.MARKDOWN)
                }

                is UserState.AwaitingEventShortDesc -> {
                    stateStorage.set(userId, UserState.AwaitingEventDescription(state.title, text, state.visibility))
                    bot.sendMessage(chatId, "Шаг 3/8 — введи *полное описание*: что будет, кому подойдёт, что взять с собой.", parseMode = ParseMode.MARKDOWN)
                }

                is UserState.AwaitingEventDescription -> {
                    stateStorage.set(userId, UserState.AwaitingEventAddress(state.title, state.shortDesc, text, state.visibility))
                    bot.sendMessage(chatId, "Шаг 4/8 — введи *адрес*.\nНапример: `Москва, Тверская 1` или `онлайн`.", parseMode = ParseMode.MARKDOWN)
                }

                is UserState.AwaitingEventAddress -> {
                    stateStorage.set(userId, UserState.AwaitingEventDate(state.title, state.shortDesc, state.description, text, state.visibility))
                    bot.sendMessage(chatId, "Шаг 5/8 — введи *дату и время*.\nФормат: `ДД.ММ.ГГГГ ЧЧ:ММ`\nНапример: `25.05.2026 18:00`", parseMode = ParseMode.MARKDOWN)
                }

                is UserState.AwaitingEventDate -> {
                    val parsed = runCatching { DateUtils.parse(text) }.getOrNull()
                    if (parsed == null) {
                        bot.sendMessage(chatId, "Неверный формат. Введите дату так: `25.05.2026 18:00`", parseMode = ParseMode.MARKDOWN)
                        return@text
                    }
                    stateStorage.set(userId, UserState.AwaitingEventCost(state.title, state.shortDesc, state.description, state.address, text, state.visibility))
                    bot.sendMessage(chatId, "Шаг 6/8 — введи *стоимость* в рублях.\n`0` — если бесплатно.", parseMode = ParseMode.MARKDOWN)
                }

                is UserState.AwaitingEventCost -> {
                    val cost = text.toLongOrNull()
                    if (cost == null || cost < 0) {
                        bot.sendMessage(chatId, "Введите целое число ≥ 0")
                        return@text
                    }
                    stateStorage.set(userId, UserState.AwaitingEventPaymentType(
                        state.title, state.shortDesc, state.description, state.address, state.startsAt, cost, state.visibility,
                    ))
                    bot.sendMessage(
                        chatId = chatId,
                        text = "Шаг 7/8 — выбери *способ оплаты*.",
                        parseMode = ParseMode.MARKDOWN,
                        replyMarkup = paymentKeyboard(),
                    )
                }

                is UserState.AwaitingEventPaymentType -> return@text  // handled by callback

                is UserState.AwaitingEventSbpPhone -> {
                    val phone = text.trim()
                    stateStorage.set(userId, UserState.AwaitingEventSbpName(
                        state.title, state.shortDesc, state.description, state.address,
                        state.startsAt, state.cost, phone, state.visibility,
                    ))
                    bot.sendMessage(chatId, "Теперь введи *имя получателя* как в СБП.", parseMode = ParseMode.MARKDOWN)
                }

                is UserState.AwaitingEventSbpName -> {
                    val sbpName = text.trim()
                    stateStorage.set(userId, UserState.AwaitingEventPhoto(
                        title = state.title, shortDesc = state.shortDesc, description = state.description,
                        address = state.address, startsAt = state.startsAt, cost = state.cost,
                        paymentType = PaymentType.ADVANCE, sbpPhone = state.sbpPhone, sbpName = sbpName,
                        visibility = state.visibility,
                    ))
                    bot.sendMessage(
                        chatId = chatId,
                        text = "Шаг 8/8 — отправь *фото* мероприятия или напиши `пропустить`.",
                        parseMode = ParseMode.MARKDOWN,
                    )
                }

                is UserState.AwaitingEventPhoto -> {
                    if (text.trim().lowercase() == "пропустить") {
                        showTagSelection(bot, chatId, userId, state, photoFileId = null)
                    } else {
                        bot.sendMessage(chatId, "Отправьте фото или напишите `пропустить`", parseMode = ParseMode.MARKDOWN)
                    }
                }

                is UserState.AwaitingEventTags -> return@text
                is UserState.AwaitingBroadcast -> return@text
                is UserState.AwaitingPaymentPhone -> return@text
                is UserState.AwaitingPaymentName -> return@text
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

        // ── Callbacks ─────────────────────────────────────────────────────────
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
                        paymentType = state.paymentType,
                        sbpPhone = state.sbpPhone,
                        sbpName = state.sbpName,
                        visibility = state.visibility,
                    )
                    stateStorage.clear(userId)
                    val paymentInfo = when (event.paymentType) {
                        PaymentType.ADVANCE -> "\n💳 Оплата заранее (СБП): `${event.sbpPhone}` — ${event.sbpName}"
                        PaymentType.ON_SITE -> "\n💵 Оплата на месте"
                    }
                    bot.answerCallbackQuery(callbackQuery.id, "Мероприятие создано!")
                    bot.editMessageText(
                        chatId = ChatId.fromId(chatId),
                        messageId = messageId,
                        text = "✅ *${event.title}* создано!$paymentInfo\n\nID: `${event.id}`\nНайди его через /events",
                        parseMode = ParseMode.MARKDOWN,
                    )
                }

                data.startsWith("cpaytype:") -> {
                    val state = stateStorage.get(userId) as? UserState.AwaitingEventPaymentType ?: return@callbackQuery
                    val type = data.removePrefix("cpaytype:")
                    bot.answerCallbackQuery(callbackQuery.id)

                    if (type == "onsite") {
                        stateStorage.set(userId, UserState.AwaitingEventPhoto(
                            title = state.title, shortDesc = state.shortDesc, description = state.description,
                            address = state.address, startsAt = state.startsAt, cost = state.cost,
                            paymentType = PaymentType.ON_SITE,
                            visibility = state.visibility,
                        ))
                        bot.editMessageText(
                            chatId = ChatId.fromId(chatId), messageId = messageId,
                            text = "Шаг 8/8 — отправь *фото* мероприятия или напиши `пропустить`.",
                            parseMode = ParseMode.MARKDOWN,
                        )
                    } else {
                        stateStorage.set(userId, UserState.AwaitingEventSbpPhone(
                        state.title, state.shortDesc, state.description,
                            state.address, state.startsAt, state.cost, state.visibility,
                        ))
                        bot.editMessageText(
                            chatId = ChatId.fromId(chatId), messageId = messageId,
                            text = "Введи *номер телефона* для приёма СБП.\nНапример: `+79991234567`",
                            parseMode = ParseMode.MARKDOWN,
                        )
                    }
                }
            }
        }
    }

    private fun paymentKeyboard() = InlineKeyboardMarkup.create(
        listOf(
            listOf(
                InlineKeyboardButton.CallbackData("💵 На месте", "cpaytype:onsite"),
                InlineKeyboardButton.CallbackData("💳 Заранее (СБП)", "cpaytype:advance"),
            ),
        ),
    )

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
                paymentType = state.paymentType,
                sbpPhone = state.sbpPhone,
                sbpName = state.sbpName,
                visibility = state.visibility,
            ),
        )
        bot.sendMessage(
            chatId = chatId,
            text = "Последний шаг — выбери *теги*, чтобы людям было проще найти мероприятие. Можно несколько. Потом нажми *Готово*.",
            parseMode = ParseMode.MARKDOWN,
            replyMarkup = TagKeyboard.forCreate(emptySet()),
        )
    }
}
