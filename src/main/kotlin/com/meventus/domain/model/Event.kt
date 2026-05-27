package com.meventus.domain.model

import java.time.Instant

data class Event(
    val id: Long,
    val ownerId: Long,
    val title: String,
    val shortDescription: String,
    val description: String,
    val photoFileId: String?,
    val tags: Set<EventTag>,
    val address: String,
    val startsAt: Instant,
    val cost: Long,
    val status: EventStatus,
    val createdAt: Instant,
    val paymentType: PaymentType = PaymentType.ON_SITE,
    val sbpPhone: String? = null,
    val sbpName: String? = null,
    val visibility: EventVisibility = EventVisibility.PUBLIC,
    val groupChatId: Long? = null,
)
