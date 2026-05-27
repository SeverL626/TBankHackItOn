package com.meventus.domain.model

import java.time.Instant

data class User(
    val telegramId: Long,
    val username: String?,
    val firstName: String,
    val createdAt: Instant,
)
