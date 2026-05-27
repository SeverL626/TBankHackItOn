package com.meventus.domain.model

import java.time.Instant

data class CustomReminder(
    val id: Long,
    val eventId: Long,
    val secondsBefore: Long,
    val message: String,
    val sent: Boolean,
    val createdAt: Instant,
)
