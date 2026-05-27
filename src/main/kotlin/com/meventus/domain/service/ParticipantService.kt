package com.meventus.domain.service

import com.meventus.domain.model.Event
import com.meventus.domain.model.Participant
import com.meventus.domain.model.PaymentStatus
import com.meventus.domain.repository.ParticipantRepository
import java.time.Instant

class ParticipantService(private val participantRepository: ParticipantRepository) {

    fun join(eventId: Long, userId: Long): Participant =
        participantRepository.add(Participant(eventId, userId, Instant.now()))

    fun joinIfAbsent(eventId: Long, userId: Long): Participant? {
        if (participantRepository.isParticipant(eventId, userId)) return null
        return participantRepository.add(Participant(eventId, userId, Instant.now()))
    }

    fun joinWithPayment(eventId: Long, userId: Long, phone: String, name: String): Participant =
        participantRepository.add(
            Participant(
                eventId = eventId,
                userId = userId,
                joinedAt = Instant.now(),
                paymentStatus = PaymentStatus.PENDING,
                payerPhone = phone,
                payerName = name,
            ),
        )

    fun leave(eventId: Long, userId: Long) =
        participantRepository.remove(eventId, userId)

    fun isParticipant(eventId: Long, userId: Long): Boolean =
        participantRepository.isParticipant(eventId, userId)

    fun findParticipant(eventId: Long, userId: Long): Participant? =
        participantRepository.findParticipant(eventId, userId)

    fun listByEvent(eventId: Long): List<Participant> =
        participantRepository.findByEvent(eventId)

    fun listEventsByUser(userId: Long): List<Event> =
        participantRepository.findEventsByUser(userId)

    fun updateContribution(eventId: Long, userId: Long, amount: Long) =
        participantRepository.updateContribution(eventId, userId, amount)

    fun confirmPayment(eventId: Long, userId: Long, cost: Long) =
        participantRepository.updatePaymentStatus(eventId, userId, PaymentStatus.CONFIRMED, contributed = cost)

    fun rejectPayment(eventId: Long, userId: Long) =
        participantRepository.updatePaymentStatus(eventId, userId, PaymentStatus.REJECTED)
}
