package com.meventus.domain.repository

import com.meventus.domain.model.Event
import com.meventus.domain.model.EventTag
import java.time.Instant

interface EventRepository {
    fun findById(id: Long): Event?
    fun findUpcoming(now: Instant = Instant.now()): List<Event>
    fun findUpcomingAll(now: Instant = Instant.now()): List<Event>
    fun findByTags(tags: Set<EventTag>, now: Instant = Instant.now()): List<Event>
    fun findByOwner(ownerId: Long): List<Event>
    fun save(event: Event): Event
    fun delete(id: Long)
}
