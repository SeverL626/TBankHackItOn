package com.meventus.bot.notifications

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.meventus.domain.model.Event
import com.meventus.domain.model.EventStatus
import com.meventus.domain.service.CustomReminderService
import com.meventus.domain.service.EventService
import com.meventus.domain.service.ParticipantService
import com.meventus.domain.service.TechUpdateService
import com.meventus.util.DateUtils
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class EventReminderService(
    private val bot: Bot,
    private val eventService: EventService,
    private val participantService: ParticipantService,
    private val customReminderService: CustomReminderService,
    private val stateService: TechUpdateService,
) {
    private val executor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "event-reminder-service").apply { isDaemon = true }
    }

    fun start() {
        executor.scheduleWithFixedDelay(::sendDueReminders, 1, 5, TimeUnit.SECONDS)
    }

    private fun sendDueReminders() {
        runCatching {
            val now = Instant.now()
            eventService.listUpcomingForNotifications().forEach { event ->
                dueStandardReminder(event, now)?.let { reminder ->
                    if (stateService.markOnce("reminder.standard.${event.id}.${reminder.secondsBefore}")) {
                        notifyEvent(event, reminder.label)
                    }
                }
            }
            sendCustomReminders(now)
        }
    }

    private fun dueStandardReminder(event: Event, now: Instant): StandardReminder? {
        val secondsLeft = Duration.between(now, event.startsAt).seconds
        return STANDARD_REMINDERS.firstOrNull { reminder ->
            secondsLeft in (reminder.secondsBefore - DELIVERY_WINDOW_SECONDS + 1)..reminder.secondsBefore
        }
    }

    private fun sendCustomReminders(now: Instant) {
        customReminderService.findPending().forEach { reminder ->
            val event = eventService.findById(reminder.eventId) ?: run {
                customReminderService.markSent(reminder.id)
                return@forEach
            }
            if (event.status != EventStatus.PUBLISHED) {
                customReminderService.markSent(reminder.id)
                return@forEach
            }
            val sendAt = event.startsAt.minusSeconds(reminder.secondsBefore)
            if (now.isBefore(sendAt)) return@forEach
            if (Duration.between(sendAt, now).seconds > DELIVERY_WINDOW_SECONDS) {
                customReminderService.markSent(reminder.id)
                return@forEach
            }
            val text = "⏰ *${event.title}*\n\n${reminder.message}\n\nДата: ${DateUtils.format(event.startsAt)}\nАдрес: ${event.address}"
            sendEventMessage(event, text)
            customReminderService.markSent(reminder.id)
        }
    }

    private fun notifyEvent(event: Event, window: String) {
        val text = "⏰ Напоминание: *${event.title}*\n" +
            "До начала: *$window*\n" +
            "Дата: ${DateUtils.format(event.startsAt)}\n" +
            "Адрес: ${event.address}"
        if (event.groupChatId != null) {
            sendEventMessage(event, text)
            return
        }

        sendEventMessage(event, text)
    }

    private fun sendEventMessage(event: Event, text: String) {
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

    private data class StandardReminder(
        val secondsBefore: Long,
        val label: String,
    )

    companion object {
        private const val DELIVERY_WINDOW_SECONDS = 60L

        private val STANDARD_REMINDERS = listOf(
            StandardReminder(24 * 60 * 60L, "24 часа"),
            StandardReminder(2 * 60 * 60L, "2 часа"),
            StandardReminder(15 * 60L, "15 минут"),
        )
    }
}
