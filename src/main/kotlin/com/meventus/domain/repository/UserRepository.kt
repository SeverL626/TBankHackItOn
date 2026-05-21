package com.meventus.domain.repository

import com.meventus.domain.model.User

interface UserRepository {
    fun findByTelegramId(telegramId: Long): User?
    fun save(user: User): User
}
