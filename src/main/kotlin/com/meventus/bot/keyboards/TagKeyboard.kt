package com.meventus.bot.keyboards

import com.meventus.domain.model.EventTag
import com.meventus.domain.model.bitmaskToTags
import com.meventus.domain.model.toBitmask
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton

object TagKeyboard {

    fun forCreate(selected: Set<EventTag>, helpExpanded: Boolean = false): InlineKeyboardMarkup =
        build(
            selected,
            togglePrefix = "ctag",
            doneText = "✅ Готово",
            donePrefix = "ctag_done",
            helpCallback = "chelp:tags:${if (helpExpanded) "close" else "open"}",
            helpText = if (helpExpanded) "Свернуть подсказку" else "ℹ️ Что это значит?",
        )

    fun forFilter(selected: Set<EventTag>): InlineKeyboardMarkup =
        build(
            selected,
            togglePrefix = "filter",
            doneText = if (selected.isEmpty()) "🔍 Показать все" else "🔍 Найти",
            donePrefix = "fsearch",
        )

    private fun build(
        selected: Set<EventTag>,
        togglePrefix: String,
        doneText: String,
        donePrefix: String,
        helpCallback: String? = null,
        helpText: String = "ℹ️ Что это значит?",
    ): InlineKeyboardMarkup {
        val bitmask = selected.toBitmask()
        val rows = mutableListOf(
            listOf(tagBtn(EventTag.IT, selected, togglePrefix), tagBtn(EventTag.SPORT, selected, togglePrefix)),
            listOf(tagBtn(EventTag.OUTDOORS, selected, togglePrefix), tagBtn(EventTag.INDOORS, selected, togglePrefix)),
            listOf(InlineKeyboardButton.CallbackData(doneText, "$donePrefix:$bitmask")),
        )
        if (helpCallback != null) rows += listOf(InlineKeyboardButton.CallbackData(helpText, helpCallback))
        return InlineKeyboardMarkup.create(rows)
    }

    private fun tagBtn(tag: EventTag, selected: Set<EventTag>, prefix: String): InlineKeyboardButton {
        val isOn = tag in selected
        val newSet = if (isOn) selected - tag else selected + tag
        val icon = if (isOn) "✅" else "⬜"
        return InlineKeyboardButton.CallbackData(
            "$icon ${tag.emoji} ${tag.displayName}",
            "$prefix:${newSet.toBitmask()}",
        )
    }

    fun parseBitmask(data: String): Set<EventTag> =
        bitmaskToTags(data.toIntOrNull() ?: 0)
}
