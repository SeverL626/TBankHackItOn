package com.meventus.bot.handlers

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.github.kotlintelegrambot.entities.keyboard.WebAppInfo
import com.meventus.bot.commands.ListEventsCommand
import com.meventus.bot.keyboards.CreateEventKeyboard
import com.meventus.bot.messages.Messages
import com.meventus.bot.states.StateStorage
import com.meventus.bot.states.UserState
import com.meventus.domain.service.EventService
import com.meventus.domain.service.ParticipantService
import com.meventus.util.DateUtils

val MENU_BUTTONS = mapOf(
    "📋 Мероприятия" to "events",
    "🔎 Найти" to "events",
    "⭐ Мои события" to "mine",
    "👤 Мои" to "mine",
    "➕ Создать событие" to "new",
    "➕ Создать" to "new",
    "📢 Рассылка" to "broadcast",
    "📊 Статистика" to "stats",
    "🌐 Mini App" to "stats",
    "❓ Помощь" to "help",
)

class MenuKeyboardHandler(
    private val eventService: EventService,
    private val participantService: ParticipantService,
    private val stateStorage: StateStorage,
    private val webAppUrl: String,
) {
    fun register(dispatcher: Dispatcher) {
        dispatcher.text {
            val userId = message.from?.id ?: return@text
            val text = message.text ?: return@text
            if (message.chat.id < 0) return@text
            if (stateStorage.get(userId) !is UserState.Idle) return@text

            val chatId = ChatId.fromId(message.chat.id)

            when (text) {
                "📋 Мероприятия", "🔎 Найти" -> {
                    ListEventsCommand.sendEventList(
                        bot = bot,
                        chatId = chatId,
                        events = eventService.listUpcoming(),
                        participantService = participantService,
                        userId = userId,
                    )
                }

                "⭐ Мои события", "👤 Мои" -> {
                    val owned = eventService.listByOwner(userId)
                    val joined = participantService.listEventsByUser(userId).filter { it.ownerId != userId }
                    if (owned.isEmpty() && joined.isEmpty()) {
                        bot.sendMessage(
                            chatId,
                            "У вас пока нет мероприятий.\n\nЧтобы записаться: *🔎 Найти*.\nЧтобы создать своё: *➕ Создать*.",
                            parseMode = ParseMode.MARKDOWN,
                        )
                        return@text
                    }
                    val body = buildString {
                        appendLine("*Мои мероприятия*")
                        appendLine("Открой карточку или управление кнопкой ниже.")
                        appendLine()
                        if (joined.isNotEmpty()) {
                            appendLine("*Участвую:*")
                            joined.forEachIndexed { index, event ->
                                appendLine("${index + 1}. ${event.title} · ${DateUtils.format(event.startsAt)}")
                            }
                            appendLine()
                        }
                        if (owned.isNotEmpty()) {
                            appendLine("*Организую:*")
                            owned.forEachIndexed { index, event ->
                                appendLine("${index + 1}. ${event.title} · ${DateUtils.format(event.startsAt)}")
                            }
                        }
                    }
                    val rows = mutableListOf<List<InlineKeyboardButton>>()
                    joined.forEach { event ->
                        rows += listOf(InlineKeyboardButton.CallbackData("Открыть: ${event.title.take(24)}", "edetail:${event.id}"))
                    }
                    owned.forEach { event ->
                        rows += listOf(InlineKeyboardButton.CallbackData("Управлять: ${event.title.take(22)}", "manage:${event.id}"))
                    }
                    bot.sendMessage(
                        chatId = chatId,
                        text = body,
                        parseMode = ParseMode.MARKDOWN,
                        replyMarkup = InlineKeyboardMarkup.create(rows),
                    )
                }

                "➕ Создать событие", "➕ Создать" -> {
                    stateStorage.set(userId, UserState.AwaitingEventTitle())
                    bot.sendMessage(
                        chatId,
                        "Создание мероприятия.\n\nВ Mini App удобнее: там форма, редактирование и админ-панель. Но можно полностью продолжить в чате — сначала выбери видимость.",
                        parseMode = ParseMode.MARKDOWN,
                        replyMarkup = CreateEventKeyboard.entry(webAppUrl),
                    )
                }

                "📢 Рассылка" -> {
                    val events = eventService.listByOwner(userId)
                    if (events.isEmpty()) {
                        bot.sendMessage(chatId, "У вас нет организованных мероприятий.")
                        return@text
                    }
                    val buttons = events.map { e ->
                        val count = participantService.listByEvent(e.id).size
                        listOf(InlineKeyboardButton.CallbackData("📋 ${e.title} — $count уч.", "bcast:${e.id}"))
                    }
                    bot.sendMessage(
                        chatId = chatId,
                        text = "📢 *Рассылка*\n\nВыберите мероприятие:",
                        parseMode = ParseMode.MARKDOWN,
                        replyMarkup = InlineKeyboardMarkup.create(buttons),
                    )
                }

                "📊 Статистика", "🌐 Mini App" -> {
                    val canOpenWebApp = webAppUrl.startsWith("https://")
                    val markup = if (canOpenWebApp) {
                        InlineKeyboardMarkup.create(
                            listOf(InlineKeyboardButton.WebApp("🌐 Открыть Mini App", WebAppInfo(webAppUrl))),
                        )
                    } else null
                    val hint = if (canOpenWebApp) {
                        "В Mini App удобнее смотреть афишу, записываться, создавать и редактировать мероприятия."
                    } else {
                        "Mini App не откроется в Telegram, пока WEBAPP_URL не начинается с https://. Сейчас: $webAppUrl"
                    }
                    bot.sendMessage(chatId, hint, replyMarkup = markup)
                }

                "❓ Помощь" -> {
                    bot.sendMessage(chatId, Messages.HELP, parseMode = ParseMode.MARKDOWN)
                }
            }
        }
    }
}
