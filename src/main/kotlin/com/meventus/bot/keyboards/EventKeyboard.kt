package com.meventus.bot.keyboards

import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton

object EventKeyboard {
    fun forEvent(eventId: Long, joined: Boolean): InlineKeyboardMarkup {
        val toggle = if (joined) {
            InlineKeyboardButton.CallbackData(text = "Отменить участие", callbackData = "leave:$eventId")
        } else {
            InlineKeyboardButton.CallbackData(text = "Я иду", callbackData = "join:$eventId")
        }
        return InlineKeyboardMarkup.create(
            listOf(toggle),
            listOf(InlineKeyboardButton.CallbackData(text = "Участники", callbackData = "info:$eventId")),
        )
    }
}
