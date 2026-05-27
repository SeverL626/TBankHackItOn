package com.meventus.bot.keyboards

import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.github.kotlintelegrambot.entities.keyboard.WebAppInfo

object CreateEventKeyboard {
    fun entry(webAppUrl: String): InlineKeyboardMarkup {
        val rows = mutableListOf<List<InlineKeyboardButton>>()
        if (webAppUrl.startsWith("https://")) {
            rows += listOf(InlineKeyboardButton.WebApp("🌐 Экспериментальное приложение", WebAppInfo(webAppUrl)))
        }
        rows += listOf(
            InlineKeyboardButton.CallbackData("🌍 Публичное", "cvis:PUBLIC"),
            InlineKeyboardButton.CallbackData("🔒 Приватное", "cvis:PRIVATE"),
        )
        rows += listOf(InlineKeyboardButton.CallbackData("ℹ️ Что это значит?", "chelp:visibility:open"))
        return InlineKeyboardMarkup.create(rows)
    }
}
