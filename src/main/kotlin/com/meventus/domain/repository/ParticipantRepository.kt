package com.meventus.domain.repository

import com.meventus.domain.model.Event
import com.meventus.domain.model.Participant

interface ParticipantRepository {
    fun add(participant: Participant): Participant
    fun remove(eventId: Long, userId: Long)
    fun findByEvent(eventId: Long): List<Participant>
    fun findEventsByUser(userId: Long): List<Event>
    fun isParticipant(eventId: Long, userId: Long): Boolean
    fun updateContribution(eventId: Long, userId: Long, amount: Long)
}
