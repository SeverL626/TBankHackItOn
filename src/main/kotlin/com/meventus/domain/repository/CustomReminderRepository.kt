package com.meventus.domain.repository

import com.meventus.domain.model.CustomReminder

interface CustomReminderRepository {
    fun save(reminder: CustomReminder): CustomReminder
    fun findPending(): List<CustomReminder>
    fun markSent(id: Long)
    fun deleteByEvent(eventId: Long)
}
