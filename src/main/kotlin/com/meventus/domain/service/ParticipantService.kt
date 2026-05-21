package com.meventus.domain.service

import com.meventus.domain.model.Event
import com.meventus.domain.model.Participant
import com.meventus.domain.repository.ParticipantRepository
import java.time.Instant

class ParticipantService(private val participantRepository: ParticipantRepository) {

    fun join(eventId: Long, userId: Long): Participant =
        participantRepository.add(Participant(eventId, userId, Instant.now()))

    fun leave(eventId: Long, userId: Long) =
        participantRepository.remove(eventId, userId)

    fun isParticipant(eventId: Long, userId: Long): Boolean =
        participantRepository.isParticipant(eventId, userId)

    fun listByEvent(eventId: Long): List<Participant> =
        participantRepository.findByEvent(eventId)

    fun listEventsByUser(userId: Long): List<Event> =
        participantRepository.findEventsByUser(userId)

    fun updateContribution(eventId: Long, userId: Long, amount: Long) =
        participantRepository.updateContribution(eventId, userId, amount)
}
