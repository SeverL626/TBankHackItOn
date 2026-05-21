package com.meventus.domain.service

import com.meventus.domain.model.Event
import com.meventus.domain.model.Participant
import com.meventus.domain.repository.ParticipantRepository
import java.time.Instant

class ParticipantService(
    private val participantRepository: ParticipantRepository,
) {
    fun join(eventId: Long, userId: Long): Participant =
        participantRepository.add(Participant(eventId, userId, Instant.now()))

    fun leave(eventId: Long, userId: Long) =
        participantRepository.remove(eventId, userId)

    fun listEventsByUser(userId: Long): List<Event> =
        participantRepository.listEventsByUser(userId)

    fun listByEvent(eventId: Long): List<Participant> =
        participantRepository.listByEvent(eventId)
}
