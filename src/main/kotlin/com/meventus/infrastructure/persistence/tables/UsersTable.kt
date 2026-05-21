package com.meventus.infrastructure.persistence.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object UsersTable : Table("users") {
    val telegramId = long("telegram_id")
    val username = varchar("username", 64).nullable()
    val firstName = varchar("first_name", 128)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(telegramId)
}
