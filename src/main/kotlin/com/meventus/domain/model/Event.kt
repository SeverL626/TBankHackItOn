package com.meventus.domain.model

import java.time.Instant

data class Event(
    val id: Long,
    val ownerId: Long,
    val title: String,
    val description: String?,
    val location: String?,
    val startsAt: Instant,
    val capacity: Int?,
    val status: EventStatus,
    val createdAt: Instant,
)
