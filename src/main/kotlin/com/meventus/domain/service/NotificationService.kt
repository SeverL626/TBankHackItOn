package com.meventus.domain.service

import com.meventus.domain.model.Event

interface NotificationService {
    fun notifyEventCreated(event: Event)
    fun notifyEventReminder(event: Event)
    fun notifyEventCancelled(event: Event)
}
