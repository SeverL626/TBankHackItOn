package com.meventus.bot.commands

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.KeyboardReplyMarkup
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.keyboard.KeyboardButton
import com.meventus.bot.cleanup.MessageCleaner
import com.meventus.bot.keyboards.CreateEventKeyboard
import com.meventus.bot.messages.Messages
import com.meventus.bot.states.StateStorage
import com.meventus.bot.states.UserState
import com.meventus.domain.service.UserService

class StartCommand(
    private val userService: UserService,
    private val stateStorage: StateStorage,
    private val webAppUrl: String,
) : Command {
    override val name = "start"

    private val menuKeyboard = KeyboardReplyMarkup(
        keyboard = listOf(
            listOf(KeyboardButton("🔎 Найти"), KeyboardButton("➕ Создать")),
            listOf(KeyboardButton("👤 Мои"), KeyboardButton("🌐 Эксперимент")),
            listOf(KeyboardButton("📢 Рассылка"), KeyboardButton("❓ Помощь")),
        ),
        resizeKeyboard = true,
    )

    override fun register(dispatcher: Dispatcher) {
        dispatcher.command(name) {
            if (message.chat.id < 0) {
                MessageCleaner.deleteLater(bot, message.chat.id, message.messageId, 20)
                return@command
            }
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
                stateStorage.set(from.id, UserState.AwaitingEventTitle(groupChatId = groupChatId))
                bot.sendMessage(
                    chatId = ChatId.fromId(message.chat.id),
                    text = "Создаём мероприятие для группы.\n\nМожно пройти всё здесь, в чате. Мини-приложение пока экспериментальное, но в нём удобнее заполнять форму. Сначала выбери видимость: только эта группа или публичная афиша.",
                    parseMode = ParseMode.MARKDOWN,
                    replyMarkup = menuKeyboard,
                )
                bot.sendMessage(
                    chatId = ChatId.fromId(message.chat.id),
                    text = "Выбери видимость мероприятия:",
                    replyMarkup = CreateEventKeyboard.entry(webAppUrl),
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
