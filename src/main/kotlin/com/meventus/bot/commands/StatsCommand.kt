package com.meventus.bot.commands

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.github.kotlintelegrambot.entities.keyboard.WebAppInfo
import com.meventus.bot.stats.StatsStorage

class StatsCommand(private val webAppUrl: String) : Command {
    override val name = "stats"

    override fun register(dispatcher: Dispatcher) {
        dispatcher.command(name) {
            if (message.chat.id < 0) return@command
            val userId = message.from?.id ?: return@command
            val stats = StatsStorage.get(userId)
            val canOpenWebApp = webAppUrl.startsWith("https://")
            val webAppHint = if (canOpenWebApp) {
                ""
            } else {
                "\n\nMini App не откроется в Telegram, пока WEBAPP_URL не начинается с https://. Сейчас: `$webAppUrl`"
            }
            val text = """
                📊 *Ваша статистика*

                Сообщений отправлено: *${stats.messageCount}*
                Самое популярное слово: *${stats.topWord ?: "—"}*
            """.trimIndent() + webAppHint

            val markup = if (canOpenWebApp) {
                InlineKeyboardMarkup.create(
                    listOf(InlineKeyboardButton.WebApp("Открыть мини-приложение", WebAppInfo(webAppUrl))),
                )
            } else null

            bot.sendMessage(
                chatId = ChatId.fromId(message.chat.id),
                text = text,
                parseMode = ParseMode.MARKDOWN,
                replyMarkup = markup,
            )
        }
    }
}
