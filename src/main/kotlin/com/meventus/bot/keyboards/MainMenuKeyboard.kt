package com.meventus.bot.keyboards

import com.github.kotlintelegrambot.entities.KeyboardReplyMarkup
import com.github.kotlintelegrambot.entities.keyboard.KeyboardButton

object MainMenuKeyboard {
    fun build(): KeyboardReplyMarkup = KeyboardReplyMarkup(
        keyboard = listOf(
            listOf(KeyboardButton("Создать"), KeyboardButton("Афиша")),
            listOf(KeyboardButton("Мои события"), KeyboardButton("Помощь")),
        ),
        resizeKeyboard = true,
    )
}
