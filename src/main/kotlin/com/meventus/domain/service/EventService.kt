package com.meventus.domain.service

import com.meventus.domain.model.Event
import com.meventus.domain.model.EventStatus
import com.meventus.domain.model.EventTag
import com.meventus.domain.repository.EventRepository
import java.time.Instant

class EventService(private val eventRepository: EventRepository) {

    fun create(
        ownerId: Long,
        title: String,
        shortDescription: String,
        description: String,
        address: String,
        startsAt: Instant,
        cost: Long,
        photoFileId: String?,
        tags: Set<EventTag>,
    ): Event = eventRepository.save(
        Event(
            id = 0,
            ownerId = ownerId,
            title = title,
            shortDescription = shortDescription,
            description = description,
            photoFileId = photoFileId,
            tags = tags,
            address = address,
            startsAt = startsAt,
            cost = cost,
            status = EventStatus.PUBLISHED,
            createdAt = Instant.now(),
        ),
    )

    fun findById(id: Long): Event? = eventRepository.findById(id)

    fun listUpcoming(): List<Event> = eventRepository.findUpcoming()

    fun listByTags(tags: Set<EventTag>): List<Event> =
        if (tags.isEmpty()) eventRepository.findUpcoming()
        else eventRepository.findByTags(tags)

    fun listByOwner(ownerId: Long): List<Event> = eventRepository.findByOwner(ownerId)

    fun cancel(eventId: Long) {
        val event = eventRepository.findById(eventId) ?: return
        eventRepository.save(event.copy(status = EventStatus.CANCELLED))
    }
}
