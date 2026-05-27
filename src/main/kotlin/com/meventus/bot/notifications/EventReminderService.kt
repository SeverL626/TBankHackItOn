package com.meventus.bot.notifications

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.meventus.domain.model.Event
import com.meventus.domain.service.EventService
import com.meventus.domain.service.ParticipantService
import com.meventus.util.DateUtils
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class EventReminderService(
    private val bot: Bot,
    private val eventService: EventService,
    private val participantService: ParticipantService,
) {
    private val sent = ConcurrentHashMap.newKeySet<String>()
    private val executor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "event-reminder-service").apply { isDaemon = true }
    }

    fun start() {
        executor.scheduleWithFixedDelay(::sendDueReminders, 1, 10, TimeUnit.MINUTES)
    }

    private fun sendDueReminders() {
        runCatching {
            val now = Instant.now()
            eventService.listUpcomingForNotifications().forEach { event ->
                reminderWindow(event, now)?.let { window ->
                    val key = "${event.id}:$window"
                    if (sent.add(key)) notifyEvent(event, window)
                }
            }
        }
    }

    private fun reminderWindow(event: Event, now: Instant): String? {
        val minutesLeft = Duration.between(now, event.startsAt).toMinutes()
        return when (minutesLeft) {
            in 23 * 60..24 * 60 -> "24 часа"
            in 110..120 -> "2 часа"
            in 0..15 -> "15 минут"
            else -> null
        }
    }

    private fun notifyEvent(event: Event, window: String) {
        val text = "⏰ Напоминание: *${event.title}*\n" +
            "До начала: *$window*\n" +
            "Дата: ${DateUtils.format(event.startsAt)}\n" +
            "Адрес: ${event.address}"
        if (event.groupChatId != null) {
            runCatching {
                bot.sendMessage(
                    chatId = ChatId.fromId(event.groupChatId),
                    text = text,
                    parseMode = ParseMode.MARKDOWN,
                )
            }
            return
        }

        val recipients = (participantService.listByEvent(event.id).map { it.userId } + event.ownerId).distinct()
        recipients.forEach { userId ->
            runCatching {
                bot.sendMessage(
                    chatId = ChatId.fromId(userId),
                    text = text,
                    parseMode = ParseMode.MARKDOWN,
                )
            }
        }
    }
}
