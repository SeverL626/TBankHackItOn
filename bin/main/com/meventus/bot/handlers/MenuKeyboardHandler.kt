package com.meventus.bot.handlers

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.github.kotlintelegrambot.entities.keyboard.WebAppInfo
import com.meventus.bot.states.StateStorage
import com.meventus.bot.states.UserState
import com.meventus.domain.service.EventService
import com.meventus.domain.service.ParticipantService
import com.meventus.util.DateUtils

val MENU_BUTTONS = mapOf(
    "📋 Мероприятия" to "events",
    "⭐ Мои события" to "mine",
    "➕ Создать событие" to "new",
    "📢 Рассылка" to "broadcast",
    "📊 Статистика" to "stats",
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
            if (stateStorage.get(userId) !is UserState.Idle) return@text

            val chatId = ChatId.fromId(message.chat.id)

            when (text) {
                "📋 Мероприятия" -> {
                    val events = eventService.listUpcoming().take(10)
                    if (events.isEmpty()) {
                        bot.sendMessage(chatId, "Ближайших мероприятий нет.")
                        return@text
                    }
                    val reply = buildString {
                        appendLine("*Ближайшие мероприятия:*")
                        events.forEach { e ->
                            appendLine("• *${e.title}* — ${DateUtils.format(e.startsAt)}")
                            if (e.shortDescription.isNotBlank()) appendLine("  ${e.shortDescription}")
                        }
                        appendLine("\nИспользуй /events для фильтра по тегам")
                    }
                    bot.sendMessage(chatId, reply, parseMode = ParseMode.MARKDOWN)
                }

                "⭐ Мои события" -> {
                    val owned = eventService.listByOwner(userId)
                    val joined = participantService.listEventsByUser(userId).filter { it.ownerId != userId }
                    val reply = buildString {
                        appendLine("*Вы организуете:*")
                        if (owned.isEmpty()) appendLine("—") else owned.forEach { appendLine("• ${it.title} — ${DateUtils.format(it.startsAt)}") }
                        appendLine()
                        appendLine("*Вы участвуете:*")
                        if (joined.isEmpty()) appendLine("—") else joined.forEach { appendLine("• ${it.title} — ${DateUtils.format(it.startsAt)}") }
                    }
                    bot.sendMessage(chatId, reply, parseMode = ParseMode.MARKDOWN)
                }

                "➕ Создать событие" -> {
                    stateStorage.set(userId, UserState.AwaitingEventTitle)
                    bot.sendMessage(chatId, "Создаём мероприятие!\n\nШаг 1/7 — Введите *название*:", parseMode = ParseMode.MARKDOWN)
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

                "📊 Статистика" -> {
                    val canOpenWebApp = webAppUrl.startsWith("https://")
                    val markup = if (canOpenWebApp) {
                        InlineKeyboardMarkup.create(
                            listOf(InlineKeyboardButton.WebApp("Открыть мини‑приложение", WebAppInfo(webAppUrl))),
                        )
                    } else null
                    val hint = if (canOpenWebApp) {
                        "📊 Откройте мини‑приложение для статистики и управления мероприятиями:"
                    } else {
                        "Mini App не откроется в Telegram, пока WEBAPP_URL не начинается с https://. Сейчас: $webAppUrl"
                    }
                    bot.sendMessage(chatId, hint, replyMarkup = markup)
                }
            }
        }
    }
}
