package com.meventus.infrastructure.scheduler

import com.meventus.domain.service.EventService
import com.meventus.domain.service.NotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

class ReminderScheduler(
    private val eventService: EventService,
    private val notificationService: NotificationService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        scope.launch {
            while (true) {
                eventService.listUpcoming().forEach { notificationService.notifyEventReminder(it) }
                delay(5.minutes)
            }
        }
    }
}
