package com.meventus.domain.service

import com.meventus.domain.model.Event
import com.meventus.domain.model.EventRegistrationMode
import com.meventus.domain.model.EventStatus
import com.meventus.domain.model.EventTag
import com.meventus.domain.model.EventVisibility
import com.meventus.domain.model.PaymentType
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
        paymentType: PaymentType = PaymentType.ON_SITE,
        sbpPhone: String? = null,
        sbpName: String? = null,
        visibility: EventVisibility = EventVisibility.PUBLIC,
        registrationMode: EventRegistrationMode = EventRegistrationMode.FREE,
        groupChatId: Long? = null,
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
            paymentType = paymentType,
            sbpPhone = sbpPhone,
            sbpName = sbpName,
            visibility = visibility,
            registrationMode = registrationMode,
            groupChatId = groupChatId,
        ),
    )

    fun findById(id: Long): Event? = eventRepository.findById(id)

    fun listUpcoming(): List<Event> = eventRepository.findUpcoming()

    fun listUpcomingForNotifications(): List<Event> = eventRepository.findUpcomingAll()

    fun listByTags(tags: Set<EventTag>): List<Event> =
        if (tags.isEmpty()) eventRepository.findUpcoming()
        else eventRepository.findByTags(tags)

    fun listByOwner(ownerId: Long): List<Event> = eventRepository.findByOwner(ownerId)

    fun listByGroup(groupChatId: Long): List<Event> = eventRepository.findByGroup(groupChatId)

    fun update(
        eventId: Long,
        ownerId: Long,
        title: String,
        shortDescription: String,
        description: String,
        address: String,
        startsAt: Instant,
        cost: Long,
        tags: Set<EventTag>,
        paymentType: PaymentType? = null,
        sbpPhone: String? = null,
        sbpName: String? = null,
        visibility: EventVisibility? = null,
        registrationMode: EventRegistrationMode? = null,
    ): Event? {
        val existing = eventRepository.findById(eventId) ?: return null
        if (existing.ownerId != ownerId) return null
        val newPaymentType = paymentType ?: existing.paymentType
        return eventRepository.save(
            existing.copy(
                title = title,
                shortDescription = shortDescription,
                description = description,
                address = address,
                startsAt = startsAt,
                cost = cost,
                tags = tags,
                paymentType = newPaymentType,
                sbpPhone = if (newPaymentType == PaymentType.ADVANCE) sbpPhone ?: existing.sbpPhone else null,
                sbpName = if (newPaymentType == PaymentType.ADVANCE) sbpName ?: existing.sbpName else null,
                visibility = visibility ?: existing.visibility,
                registrationMode = registrationMode ?: existing.registrationMode,
            ),
        )
    }

    fun cancel(eventId: Long) {
        val event = eventRepository.findById(eventId) ?: return
        eventRepository.save(event.copy(status = EventStatus.CANCELLED))
    }
}
