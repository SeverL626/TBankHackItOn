package com.meventus.domain.service

import com.meventus.domain.model.CustomReminder
import com.meventus.domain.repository.CustomReminderRepository
import java.time.Instant

class CustomReminderService(
    private val customReminderRepository: CustomReminderRepository,
) {
    fun create(eventId: Long, secondsBefore: Long, message: String): CustomReminder =
        customReminderRepository.save(
            CustomReminder(
                id = 0,
                eventId = eventId,
                secondsBefore = secondsBefore,
                message = message,
                sent = false,
                createdAt = Instant.now(),
            ),
        )

    fun findPending(): List<CustomReminder> = customReminderRepository.findPending()

    fun markSent(id: Long) = customReminderRepository.markSent(id)

    fun deleteByEvent(eventId: Long) = customReminderRepository.deleteByEvent(eventId)
}
