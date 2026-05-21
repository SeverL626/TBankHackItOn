package com.meventus.domain.model

import java.time.Instant

data class Participant(
    val eventId: Long,
    val userId: Long,
    val joinedAt: Instant,
)
