package com.meventus.bot.handlers

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.github.kotlintelegrambot.entities.keyboard.WebAppInfo
import com.meventus.bot.commands.ListEventsCommand
import com.meventus.bot.messages.Messages
import com.meventus.bot.states.StateStorage
import com.meventus.bot.states.UserState
import com.meventus.domain.service.EventService
import com.meventus.domain.service.ParticipantService

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
                    if (joined.isNotEmpty()) {
                        bot.sendMessage(chatId, "*Вы участвуете:*", parseMode = ParseMode.MARKDOWN)
                        joined.forEach { ListEventsCommand.sendEventCard(bot, chatId, it, participantService, userId) }
                    }
                    if (owned.isNotEmpty()) {
                        bot.sendMessage(chatId, "*Вы организуете:*", parseMode = ParseMode.MARKDOWN)
                        owned.forEach { ListEventsCommand.sendEventCard(bot, chatId, it, participantService, userId) }
                    }
                }

                "➕ Создать событие", "➕ Создать" -> {
                    stateStorage.set(userId, UserState.AwaitingEventTitle())
                    bot.sendMessage(
                        chatId,
                        "Создаём *публичное* мероприятие.\n\nЕсли нужно приватное, напиши /cancel и затем /new private.\n\nШаг 1/8 — введи *название*:",
                        parseMode = ParseMode.MARKDOWN,
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
