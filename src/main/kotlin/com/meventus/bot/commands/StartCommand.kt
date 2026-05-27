package com.meventus.bot.commands

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.KeyboardReplyMarkup
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.keyboard.KeyboardButton
import com.meventus.bot.messages.Messages
import com.meventus.bot.states.StateStorage
import com.meventus.bot.states.UserState
import com.meventus.domain.model.EventVisibility
import com.meventus.domain.service.UserService

class StartCommand(
    private val userService: UserService,
    private val stateStorage: StateStorage,
) : Command {
    override val name = "start"

    private val menuKeyboard = KeyboardReplyMarkup(
        keyboard = listOf(
            listOf(KeyboardButton("🔎 Найти"), KeyboardButton("➕ Создать")),
            listOf(KeyboardButton("👤 Мои"), KeyboardButton("🌐 Mini App")),
            listOf(KeyboardButton("📢 Рассылка"), KeyboardButton("❓ Помощь")),
        ),
        resizeKeyboard = true,
    )

    override fun register(dispatcher: Dispatcher) {
        dispatcher.command(name) {
            val from = message.from ?: return@command
            userService.registerIfAbsent(
                telegramId = from.id,
                username = from.username,
                firstName = from.firstName,
            )
            val payload = message.text
                ?.substringAfter(" ", missingDelimiterValue = "")
                ?.trim()
                .orEmpty()
            val groupChatId = payload
                .takeIf { it.startsWith("gnew_m") }
                ?.removePrefix("gnew_m")
                ?.toLongOrNull()
                ?.let { -it }
            if (groupChatId != null) {
                stateStorage.set(from.id, UserState.AwaitingEventTitle(EventVisibility.PRIVATE, groupChatId))
                bot.sendMessage(
                    chatId = ChatId.fromId(message.chat.id),
                    text = "Создаём мероприятие для группы.\n\nЯ всё спрошу тут, а в группу отправлю короткое уведомление.\n\nШаг 1/8 — введи *название*:",
                    parseMode = ParseMode.MARKDOWN,
                    replyMarkup = menuKeyboard,
                )
                return@command
            }
            bot.sendMessage(
                chatId = ChatId.fromId(message.chat.id),
                text = Messages.welcome(from.firstName),
                parseMode = ParseMode.MARKDOWN,
                replyMarkup = menuKeyboard,
            )
        }
    }
}
