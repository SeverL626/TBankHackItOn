package com.meventus.bot.keyboards

import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

object CalendarKeyboard {

    private val zone = ZoneId.of("Europe/Moscow")
    private val monthNames = listOf(
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
    )
    private val weekDays = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

    fun buildForToday(): InlineKeyboardMarkup = build(LocalDate.now(zone))

    fun build(year: Int, month: Int): InlineKeyboardMarkup = build(YearMonth.of(year, month).atDay(1))

    private fun build(reference: LocalDate): InlineKeyboardMarkup {
        val today = LocalDate.now(zone)
        val yearMonth = YearMonth.from(reference)
        val firstDayOfMonth = yearMonth.atDay(1)
        val daysInMonth = yearMonth.lengthOfMonth()
        // Adjust for Monday-first (0=Monday, 6=Sunday)
        val startOffset = (firstDayOfMonth.dayOfWeek.value % 7)

        val rows = mutableListOf<List<InlineKeyboardButton>>()

        // Header: month + year
        rows += listOf(InlineKeyboardButton.CallbackData(
            "📅 ${monthNames[yearMonth.monthValue - 1]} ${yearMonth.year}",
            "cnoop:header",
        ))

        // Weekday headers
        rows += weekDays.map { InlineKeyboardButton.CallbackData(it, "cnoop:wd") }

        // Days grid
        var currentDay = 1
        while (currentDay <= daysInMonth || (rows.size < 3 + ((daysInMonth + startOffset + 6) / 7))) {
            val row = mutableListOf<InlineKeyboardButton>()
            for (col in 0..6) {
                val cellIndex = (currentDay - 1) + startOffset
                if (cellIndex < 0 || cellIndex >= daysInMonth + startOffset || currentDay > daysInMonth) {
                    row += InlineKeyboardButton.CallbackData(" ", "cnoop:empty")
                } else {
                    val cellDate = yearMonth.atDay(currentDay)
                    val dayStr = currentDay.toString().padStart(2, '0')
                    val monthStr = yearMonth.monthValue.toString().padStart(2, '0')
                    val datePayload = "$dayStr.$monthStr.${yearMonth.year}"
                    if (cellDate.isBefore(today)) {
                        row += InlineKeyboardButton.CallbackData("·$dayStr·", "cnoop:date")
                    } else {
                        row += InlineKeyboardButton.CallbackData(dayStr, "cdate:$datePayload")
                    }
                    currentDay++
                }
            }
            if (row.isNotEmpty()) rows += row
            if (currentDay > daysInMonth) break
        }

        // Navigation
        val prevMonth = if (yearMonth.monthValue == 1) {
            Pair(yearMonth.year - 1, 12)
        } else {
            Pair(yearMonth.year, yearMonth.monthValue - 1)
        }
        val nextMonth = if (yearMonth.monthValue == 12) {
            Pair(yearMonth.year + 1, 1)
        } else {
            Pair(yearMonth.year, yearMonth.monthValue + 1)
        }
        val isCurrentMonth = yearMonth == YearMonth.from(today)

        rows += listOf(
            InlineKeyboardButton.CallbackData(
                if (isCurrentMonth) "◀" else "◀ Назад",
                if (isCurrentMonth) "cnoop:prev" else "cnav:prev:${prevMonth.first}-${prevMonth.second.toString().padStart(2, '0')}",
            ),
            InlineKeyboardButton.CallbackData("▶ Вперёд", "cnav:next:${nextMonth.first}-${nextMonth.second.toString().padStart(2, '0')}"),
            InlineKeyboardButton.CallbackData("❌ Отмена", "ccancel"),
        )

        return InlineKeyboardMarkup.create(rows)
    }
}
