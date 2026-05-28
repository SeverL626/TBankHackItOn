package com.meventus.bot.handlers

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.meventus.bot.keyboards.CalendarKeyboard
import com.meventus.bot.keyboards.TagKeyboard
import com.meventus.bot.states.StateStorage
import com.meventus.bot.states.UserState
import com.meventus.domain.model.EventRegistrationMode
import com.meventus.domain.model.EventVisibility
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
                    stateStorage.set(userId, UserState.AwaitingEventShortDesc(text, state.visibility, state.registrationMode, state.groupChatId))
                    sendHelpableStep(bot, chatId, "short")
                }

                is UserState.AwaitingEventShortDesc -> {
                    stateStorage.set(userId, UserState.AwaitingEventDescription(state.title, text, state.visibility, state.registrationMode, state.groupChatId))
                    sendHelpableStep(bot, chatId, "description")
                }

                is UserState.AwaitingEventDescription -> {
                    stateStorage.set(userId, UserState.AwaitingEventAddress(state.title, state.shortDesc, text, state.visibility, state.registrationMode, state.groupChatId))
                    sendHelpableStep(bot, chatId, "address")
                }

                is UserState.AwaitingEventAddress -> {
                    stateStorage.set(userId, UserState.AwaitingEventDate(state.title, state.shortDesc, state.description, text, state.visibility, state.registrationMode, state.groupChatId))
                    bot.sendMessage(
                        chatId = chatId,
                        text = "Шаг 5/8 — выбери *дату* мероприятия:",
                        parseMode = ParseMode.MARKDOWN,
                        replyMarkup = CalendarKeyboard.buildForToday(),
                    )
                }

                is UserState.AwaitingEventDate -> {
                    val parsed = runCatching { DateUtils.parse(text) }.getOrNull()
                    if (parsed == null) {
                        bot.sendMessage(chatId, "Неверный формат. Введите дату так: `25.05.2026 18:00`", parseMode = ParseMode.MARKDOWN)
                        return@text
                    }
                    stateStorage.set(userId, UserState.AwaitingEventCost(state.title, state.shortDesc, state.description, state.address, text, state.visibility, state.registrationMode, state.groupChatId))
                    sendHelpableStep(bot, chatId, "cost")
                }

                is UserState.AwaitingEventTime -> {
                    val timeRegex = Regex("^(\\d{1,2}):(\\d{2})$")
                    val match = timeRegex.matchEntire(text.trim())
                    if (match == null) {
                        bot.sendMessage(chatId, "Неверный формат. Введи время в формате ЧЧ:ММ (например, 18:00)")
                        return@text
                    }
                    val hours = match.groupValues[1].toInt()
                    val minutes = match.groupValues[2].toInt()
                    if (hours !in 0..23 || minutes !in 0..59) {
                        bot.sendMessage(chatId, "Часы должны быть от 0 до 23, минуты от 0 до 59.")
                        return@text
                    }
                    val fullDate = "${state.date} ${String.format("%02d:%02d", hours, minutes)}"
                    val parsed = runCatching { DateUtils.parse(fullDate) }.getOrNull()
                    if (parsed == null) {
                        bot.sendMessage(chatId, "Ошибка при обработке даты. Начните заново: /new")
                        stateStorage.clear(userId)
                        return@text
                    }
                    stateStorage.set(userId, UserState.AwaitingEventCost(
                        state.title, state.shortDesc, state.description, state.address,
                        fullDate, state.visibility, state.registrationMode, state.groupChatId,
                    ))
                    sendHelpableStep(bot, chatId, "cost")
                }

                is UserState.AwaitingEventCost -> {
                    val cost = text.toLongOrNull()
                    if (cost == null || cost < 0) {
                        bot.sendMessage(chatId, "Введите целое число ≥ 0")
                        return@text
                    }
                    if (cost == 0L) {
                        val nextState = UserState.AwaitingEventPhoto(
                            title = state.title,
                            shortDesc = state.shortDesc,
                            description = state.description,
                            address = state.address,
                            startsAt = state.startsAt,
                            cost = cost,
                            paymentType = PaymentType.ON_SITE,
                            visibility = state.visibility,
                            registrationMode = state.registrationMode,
                            groupChatId = state.groupChatId,
                        )
                        stateStorage.set(userId, nextState)
                        sendPhotoStep(bot, chatId)
                        return@text
                    }
                    stateStorage.set(userId, UserState.AwaitingEventPaymentType(
                        state.title, state.shortDesc, state.description, state.address, state.startsAt, cost, state.visibility, state.registrationMode, state.groupChatId,
                    ))
                    bot.sendMessage(
                        chatId = chatId,
                        text = stepText("payment", expanded = false),
                        parseMode = ParseMode.MARKDOWN,
                        replyMarkup = paymentKeyboard(),
                    )
                }

                is UserState.AwaitingEventPaymentType -> return@text  // handled by callback

                is UserState.AwaitingEventSbpPhone -> {
                    val phone = text.trim()
                    stateStorage.set(userId, UserState.AwaitingEventSbpName(
                        state.title, state.shortDesc, state.description, state.address,
                        state.startsAt, state.cost, phone, state.visibility, state.registrationMode, state.groupChatId,
                    ))
                    sendHelpableStep(bot, chatId, "sbpName")
                }

                is UserState.AwaitingEventSbpName -> {
                    val sbpName = text.trim()
                    stateStorage.set(userId, UserState.AwaitingEventPhoto(
                        title = state.title, shortDesc = state.shortDesc, description = state.description,
                        address = state.address, startsAt = state.startsAt, cost = state.cost,
                        paymentType = PaymentType.ADVANCE, sbpPhone = state.sbpPhone, sbpName = sbpName,
                        visibility = state.visibility,
                        registrationMode = state.registrationMode,
                        groupChatId = state.groupChatId,
                    ))
                    sendPhotoStep(bot, chatId)
                }

                is UserState.AwaitingEventPhoto -> {
                    if (text.trim().lowercase() == "пропустить") {
                        showTagSelection(bot, chatId, userId, state, photoFileId = null)
                    } else {
                        bot.sendMessage(
                            chatId = chatId,
                            text = "Отправь фото следующим сообщением или нажми *Пропустить*.",
                            parseMode = ParseMode.MARKDOWN,
                            replyMarkup = photoKeyboard(),
                        )
                    }
                }

                is UserState.AwaitingEventTags -> return@text
                is UserState.AwaitingBroadcast -> return@text
                is UserState.AwaitingEventEdit -> return@text
                is UserState.AwaitingCustomReminderTime -> return@text
                is UserState.AwaitingCustomReminderMessage -> return@text
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
                data.startsWith("chelp:") -> {
                    val parts = data.removePrefix("chelp:").split(":")
                    val step = parts.getOrNull(0) ?: return@callbackQuery
                    val expanded = parts.getOrNull(1) == "open"
                    bot.answerCallbackQuery(callbackQuery.id)
                    bot.editMessageText(
                        chatId = ChatId.fromId(chatId),
                        messageId = messageId,
                        text = stepText(step, expanded),
                        parseMode = ParseMode.MARKDOWN,
                        replyMarkup = stepKeyboardFor(step, expanded, userId),
                    )
                }

                data.startsWith("cvis:") -> {
                    val visibility = when (data.removePrefix("cvis:")) {
                        "PRIVATE" -> EventVisibility.PRIVATE
                        else -> EventVisibility.PUBLIC
                    }
                    val current = stateStorage.get(userId) as? UserState.AwaitingEventTitle ?: return@callbackQuery
                    stateStorage.set(userId, current.copy(visibility = visibility))
                    bot.answerCallbackQuery(callbackQuery.id)
                    bot.editMessageText(
                        chatId = ChatId.fromId(chatId),
                        messageId = messageId,
                        text = stepText("registration", expanded = false),
                        parseMode = ParseMode.MARKDOWN,
                        replyMarkup = registrationKeyboard(),
                    )
                }

                data.startsWith("creg:") -> {
                    val registrationMode = when (data.removePrefix("creg:")) {
                        "INVITE_ONLY" -> EventRegistrationMode.INVITE_ONLY
                        else -> EventRegistrationMode.FREE
                    }
                    val current = stateStorage.get(userId) as? UserState.AwaitingEventTitle ?: return@callbackQuery
                    stateStorage.set(userId, current.copy(registrationMode = registrationMode))
                    val visibilityText = if (current.visibility == EventVisibility.PRIVATE) "приватное" else "публичное"
                    val registrationText = if (registrationMode == EventRegistrationMode.INVITE_ONLY) "по приглашению" else "со свободной записью"
                    bot.answerCallbackQuery(callbackQuery.id)
                    bot.editMessageText(
                        chatId = ChatId.fromId(chatId),
                        messageId = messageId,
                        text = "Создаём *$visibilityText* мероприятие, запись: *$registrationText*.\n\n" + stepText("title", expanded = false),
                        parseMode = ParseMode.MARKDOWN,
                        replyMarkup = helpOnlyKeyboard("title"),
                    )
                }

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
                        registrationMode = state.registrationMode,
                        groupChatId = state.groupChatId,
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
                        text = if (state.groupChatId != null) {
                            "✅ *${event.title}* создано для группы!$paymentInfo\n\nID: `${event.id}`"
                        } else {
                            "✅ *${event.title}* создано!$paymentInfo\n\nID: `${event.id}`\nНайди его через /events"
                        },
                        parseMode = ParseMode.MARKDOWN,
                    )
                    if (state.groupChatId != null) {
                        runCatching {
                            bot.sendMessage(
                                chatId = ChatId.fromId(state.groupChatId),
                                text = "Новое мероприятие группы: *${event.title}*\nДата: ${DateUtils.format(event.startsAt)}\nОткройте /gevents, чтобы записаться.",
                                parseMode = ParseMode.MARKDOWN,
                            )
                        }
                    }
                }

                data.startsWith("cphoto:") -> {
                    val state = stateStorage.get(userId) as? UserState.AwaitingEventPhoto ?: return@callbackQuery
                    val action = data.removePrefix("cphoto:")
                    bot.answerCallbackQuery(callbackQuery.id)
                    if (action == "skip") {
                        showTagSelection(bot, ChatId.fromId(chatId), userId, state, photoFileId = null)
                    } else {
                        bot.editMessageText(
                            chatId = ChatId.fromId(chatId),
                            messageId = messageId,
                            text = stepText("photoWait", expanded = false),
                            parseMode = ParseMode.MARKDOWN,
                            replyMarkup = photoKeyboard(),
                        )
                    }
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
                            registrationMode = state.registrationMode,
                            groupChatId = state.groupChatId,
                        ))
                        bot.editMessageText(
                            chatId = ChatId.fromId(chatId), messageId = messageId,
                            text = stepText("photo", expanded = false),
                            parseMode = ParseMode.MARKDOWN,
                            replyMarkup = photoKeyboard(),
                        )
                    } else {
                        stateStorage.set(userId, UserState.AwaitingEventSbpPhone(
                            state.title, state.shortDesc, state.description,
                            state.address, state.startsAt, state.cost, state.visibility, state.registrationMode, state.groupChatId,
                        ))
                        bot.editMessageText(
                            chatId = ChatId.fromId(chatId), messageId = messageId,
                            text = stepText("sbpPhone", expanded = false),
                            parseMode = ParseMode.MARKDOWN,
                            replyMarkup = helpOnlyKeyboard("sbpPhone"),
                        )
                    }
                }

                // ── Calendar callbacks ──────────────────────────────────────────────
                data.startsWith("cdate:") -> {
                    val dateStr = data.removePrefix("cdate:")
                    val currentState = stateStorage.get(userId) as? UserState.AwaitingEventDate ?: return@callbackQuery
                    stateStorage.set(userId, UserState.AwaitingEventTime(
                        title = currentState.title,
                        shortDesc = currentState.shortDesc,
                        description = currentState.description,
                        address = currentState.address,
                        date = dateStr,
                        visibility = currentState.visibility,
                        registrationMode = currentState.registrationMode,
                        groupChatId = currentState.groupChatId,
                    ))
                    bot.answerCallbackQuery(callbackQuery.id)
                    bot.editMessageText(
                        chatId = ChatId.fromId(chatId),
                        messageId = messageId,
                        text = "Выбрана дата: *$dateStr*\n\nВведи время начала в формате ЧЧ:ММ (например, 18:00)",
                        parseMode = ParseMode.MARKDOWN,
                    )
                }

                data.startsWith("cnav:") -> {
                    val parts = data.removePrefix("cnav:").split(":")
                    val direction = parts.getOrNull(0)
                    val yearMonth = parts.getOrNull(1)?.split("-") ?: return@callbackQuery
                    val year = yearMonth.getOrNull(0)?.toIntOrNull() ?: return@callbackQuery
                    val month = yearMonth.getOrNull(1)?.toIntOrNull() ?: return@callbackQuery
                    val newYearMonth = when (direction) {
                        "prev" -> if (month == 1) Pair(year - 1, 12) else Pair(year, month - 1)
                        "next" -> if (month == 12) Pair(year + 1, 1) else Pair(year, month + 1)
                        else -> return@callbackQuery
                    }
                    bot.answerCallbackQuery(callbackQuery.id)
                    bot.editMessageReplyMarkup(
                        chatId = ChatId.fromId(chatId),
                        messageId = messageId,
                        replyMarkup = CalendarKeyboard.build(newYearMonth.first, newYearMonth.second),
                    )
                }

                data == "ccancel" -> {
                    stateStorage.clear(userId)
                    bot.answerCallbackQuery(callbackQuery.id)
                    bot.editMessageText(
                        chatId = ChatId.fromId(chatId),
                        messageId = messageId,
                        text = "Создание мероприятия отменено.",
                    )
                }

                data.startsWith("cnoop") -> {
                    val alert = when {
                        data == "cnoop:date" -> "Эта дата уже прошла"
                        data == "cnoop:prev" -> "Нельзя выбрать прошедший месяц"
                        else -> null
                    }
                    bot.answerCallbackQuery(callbackQuery.id, alert)
                }
            }
        }
    }

    private fun paymentKeyboard(expanded: Boolean = false) = InlineKeyboardMarkup.create(
        listOf(
            listOf(
                InlineKeyboardButton.CallbackData("💵 На месте", "cpaytype:onsite"),
                InlineKeyboardButton.CallbackData("💳 Заранее (СБП)", "cpaytype:advance"),
            ),
            listOf(helpButton("payment", expanded)),
        ),
    )

    private fun registrationKeyboard(expanded: Boolean = false) = InlineKeyboardMarkup.create(
        listOf(
            listOf(
                InlineKeyboardButton.CallbackData("✅ Свободная запись", "creg:FREE"),
                InlineKeyboardButton.CallbackData("🔐 По приглашению", "creg:INVITE_ONLY"),
            ),
            listOf(helpButton("registration", expanded)),
        ),
    )

    private fun visibilityKeyboard(expanded: Boolean = false) = InlineKeyboardMarkup.create(
        listOf(
            listOf(
                InlineKeyboardButton.CallbackData("🌍 Публичное", "cvis:PUBLIC"),
                InlineKeyboardButton.CallbackData("🔒 Приватное", "cvis:PRIVATE"),
            ),
            listOf(helpButton("visibility", expanded)),
        ),
    )

    private fun photoKeyboard(expanded: Boolean = false) = InlineKeyboardMarkup.create(
        listOf(
            listOf(
                InlineKeyboardButton.CallbackData("📷 Добавить фото", "cphoto:add"),
                InlineKeyboardButton.CallbackData("⏭ Пропустить", "cphoto:skip"),
            ),
            listOf(helpButton("photo", expanded)),
        ),
    )

    private fun sendPhotoStep(bot: com.github.kotlintelegrambot.Bot, chatId: ChatId) {
        bot.sendMessage(
            chatId = chatId,
            text = stepText("photo", expanded = false),
            parseMode = ParseMode.MARKDOWN,
            replyMarkup = photoKeyboard(),
        )
    }

    private fun sendHelpableStep(bot: com.github.kotlintelegrambot.Bot, chatId: ChatId, step: String) {
        bot.sendMessage(
            chatId = chatId,
            text = stepText(step, expanded = false),
            parseMode = ParseMode.MARKDOWN,
            replyMarkup = helpOnlyKeyboard(step),
        )
    }

    private fun helpOnlyKeyboard(step: String, expanded: Boolean = false) =
        InlineKeyboardMarkup.create(listOf(helpButton(step, expanded)))

    private fun helpButton(step: String, expanded: Boolean) = InlineKeyboardButton.CallbackData(
        if (expanded) "Свернуть подсказку" else "ℹ️ Что это значит?",
        "chelp:$step:${if (expanded) "close" else "open"}",
    )

    private fun stepKeyboardFor(step: String, expanded: Boolean, userId: Long): InlineKeyboardMarkup = when (step) {
        "visibility" -> visibilityKeyboard(expanded)
        "registration" -> registrationKeyboard(expanded)
        "payment" -> paymentKeyboard(expanded)
        "photo", "photoWait" -> photoKeyboard(expanded)
        "tags" -> {
            val selected = (stateStorage.get(userId) as? UserState.AwaitingEventTags)?.selectedTags.orEmpty()
            TagKeyboard.forCreate(selected, helpExpanded = expanded)
        }
        else -> helpOnlyKeyboard(step, expanded)
    }

    private fun stepText(step: String, expanded: Boolean): String {
        val base = when (step) {
            "visibility" -> "Выбери, *где будет видно* мероприятие."
            "registration" -> "Теперь выбери, *как люди смогут записаться*."
            "title" -> "Шаг 1/8 — введи *название*.\nНапример: `Kotlin meetup`"
            "short" -> "Шаг 2/8 — введи *краткое описание* для карточки.\nНапример: `Встречаемся обсудить проекты и познакомиться`"
            "description" -> "Шаг 3/8 — введи *полное описание*: что будет, кому подойдёт, что взять с собой."
            "address" -> "Шаг 4/8 — введи *адрес*.\nНапример: `Москва, Тверская 1` или `онлайн`."
            "date" -> "Шаг 5/8 — выбери дату в календаре или введи текстом.\nФормат: `ДД.ММ.ГГГГ ЧЧ:ММ`\nНапример: `25.05.2026 18:00`"
            "cost" -> "Шаг 6/8 — введи *стоимость* в рублях.\n`0` — если бесплатно."
            "payment" -> "Шаг 7/8 — выбери *способ оплаты*."
            "sbpPhone" -> "Введи *номер телефона* для приёма СБП.\nНапример: `+79991234567`"
            "sbpName" -> "Теперь введи *имя получателя* как в СБП."
            "photo", "photoWait" -> "Фото мероприятия — добавь картинку или пропусти этот шаг."
            "tags" -> "Последний шаг — выбери *теги*, чтобы людям было проще найти мероприятие. Можно несколько. Потом нажми *Готово*."
            else -> "Продолжи создание мероприятия."
        }
        if (!expanded) return base
        return base + "\n\n" + stepHelp(step)
    }

    private fun stepHelp(step: String): String = when (step) {
        "visibility" -> """
*Подсказка*
Это отвечает на вопрос: кто вообще увидит событие.

*Публичное* — попадёт в общую ленту /events и мини-приложение.
*Приватное* — не попадёт в общую ленту. Для группы это значит “только в календаре этой группы”.
""".trimIndent()
        "registration" -> """
*Подсказка*
Это отвечает на вопрос: кто сможет записаться.

*Свободная запись* — те, кто видят событие, могут нажать “Участвовать”.
*По приглашению* — участники добавляются организатором.

Приватное + свободная запись нормально: событие не видно всем, но участники группы могут сами записаться через /gevents.
""".trimIndent()
        "title" -> "*Подсказка*\nНазвание должно быть коротким и узнаваемым: `Демо-день`, `Футбол в четверг`, `Созвон команды`."
        "short" -> "*Подсказка*\nЭто текст для списка. Одной фразы достаточно: кто, зачем и почему стоит открыть карточку."
        "description" -> "*Подсказка*\nЗдесь можно написать программу, правила, что взять с собой, для кого событие и контакты организатора."
        "address" -> "*Подсказка*\nМожно указать физический адрес, кабинет, ссылку на созвон или просто `онлайн`."
        "date" -> "*Подсказка*\nПиши дату в московском формате: `27.05.2026 19:30`. Бот использует её для напоминаний."
        "cost" -> "*Подсказка*\nЕсли событие бесплатное, введи `0`. Тогда бот не будет спрашивать способ оплаты."
        "payment" -> "*Подсказка*\n`На месте` — человек сразу становится участником. `Заранее через СБП` — бот покажет реквизиты и попросит подтверждение перевода."
        "sbpPhone" -> "*Подсказка*\nЭто номер, который участники увидят перед оплатой. Он нужен только для платных событий с оплатой заранее."
        "sbpName" -> "*Подсказка*\nИмя получателя помогает участнику проверить, что перевод уходит правильному человеку."
        "photo", "photoWait" -> "*Подсказка*\nФото необязательно. Оно делает карточку заметнее, но если картинки нет — спокойно нажимай `Пропустить`."
        "tags" -> "*Подсказка*\nТеги помогают фильтровать ленту. Выбери 1-2 главных темы, лишние теги лучше не ставить."
        else -> "*Подсказка*\nМожно продолжить в чате или отменить сценарий командой /cancel."
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
                paymentType = state.paymentType,
                sbpPhone = state.sbpPhone,
                sbpName = state.sbpName,
                visibility = state.visibility,
                registrationMode = state.registrationMode,
                groupChatId = state.groupChatId,
            ),
        )
        bot.sendMessage(
            chatId = chatId,
            text = stepText("tags", expanded = false),
            parseMode = ParseMode.MARKDOWN,
            replyMarkup = TagKeyboard.forCreate(emptySet()),
        )
    }
}
