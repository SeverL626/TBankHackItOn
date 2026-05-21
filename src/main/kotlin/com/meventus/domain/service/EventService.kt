package com.meventus.domain.service

import com.meventus.domain.model.Event
import com.meventus.domain.model.EventStatus
import com.meventus.domain.repository.EventRepository
import java.time.Instant

class EventService(
    private val eventRepository: EventRepository,
) {
    fun create(
        ownerId: Long,
        title: String,
        startsAt: Instant,
        description: String? = null,
        location: String? = null,
        capacity: Int? = null,
    ): Event = eventRepository.save(
        Event(
            id = 0,
            ownerId = ownerId,
            title = title,
            description = description,
            location = location,
            startsAt = startsAt,
            capacity = capacity,
            status = EventStatus.PUBLISHED,
            createdAt = Instant.now(),
        ),
    )

    fun listUpcoming(): List<Event> = eventRepository.findUpcoming()

    fun listByOwner(ownerId: Long): List<Event> = eventRepository.findByOwner(ownerId)

    fun cancel(eventId: Long) {
        val event = eventRepository.findById(eventId) ?: return
        eventRepository.save(event.copy(status = EventStatus.CANCELLED))
    }
}
