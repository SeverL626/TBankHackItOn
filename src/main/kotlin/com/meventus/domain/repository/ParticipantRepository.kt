package com.meventus.domain.repository

import com.meventus.domain.model.Event
import com.meventus.domain.model.Participant

interface ParticipantRepository {
    fun add(participant: Participant): Participant
    fun remove(eventId: Long, userId: Long)
    fun listByEvent(eventId: Long): List<Participant>
    fun listEventsByUser(userId: Long): List<Event>
}
