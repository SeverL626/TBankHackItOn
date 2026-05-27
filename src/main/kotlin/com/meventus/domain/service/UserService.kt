package com.meventus.domain.service

import com.meventus.domain.model.User
import com.meventus.domain.repository.UserRepository
import java.time.Instant

class UserService(
    private val userRepository: UserRepository,
) {
    fun registerIfAbsent(telegramId: Long, username: String?, firstName: String): User {
        return userRepository.findByTelegramId(telegramId)
            ?: userRepository.save(
                User(
                    telegramId = telegramId,
                    username = username,
                    firstName = firstName,
                    createdAt = Instant.now(),
                ),
            )
    }

    fun findByUsername(username: String): User? =
        userRepository.findByUsername(username.removePrefix("@"))
}
