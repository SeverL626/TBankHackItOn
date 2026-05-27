package com.meventus.bot.cleanup

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.types.TelegramBotResult
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object MessageCleaner {
    private val executor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "message-cleaner").apply { isDaemon = true }
    }

    fun deleteLater(bot: Bot, chatId: Long, messageId: Long?, delaySeconds: Long) {
        if (messageId == null || delaySeconds <= 0) return
        executor.schedule(
            {
                runCatching {
                    bot.deleteMessage(ChatId.fromId(chatId), messageId)
                }
            },
            delaySeconds,
            TimeUnit.SECONDS,
        )
    }

    fun deleteLater(bot: Bot, chatId: Long, result: TelegramBotResult<Message>, delaySeconds: Long) {
        deleteLater(bot, chatId, result.getOrNull()?.messageId, delaySeconds)
    }
}
