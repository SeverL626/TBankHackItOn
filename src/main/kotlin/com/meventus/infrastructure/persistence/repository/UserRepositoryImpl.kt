package com.meventus.infrastructure.persistence.repository

import com.meventus.domain.model.User
import com.meventus.domain.repository.UserRepository
import com.meventus.infrastructure.persistence.tables.UsersTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class UserRepositoryImpl : UserRepository {

    override fun findByTelegramId(telegramId: Long): User? = transaction {
        UsersTable.selectAll()
            .where { UsersTable.telegramId eq telegramId }
            .map(::toUser)
            .firstOrNull()
    }

    override fun save(user: User): User = transaction {
        UsersTable.insert {
            it[telegramId] = user.telegramId
            it[username] = user.username
            it[firstName] = user.firstName
            it[createdAt] = user.createdAt
        }
        user
    }

    private fun toUser(row: ResultRow): User = User(
        telegramId = row[UsersTable.telegramId],
        username = row[UsersTable.username],
        firstName = row[UsersTable.firstName],
        createdAt = row[UsersTable.createdAt],
    )
}
