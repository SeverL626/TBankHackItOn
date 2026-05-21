package com.meventus.infrastructure.persistence.tables

import com.meventus.domain.model.EventStatus
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestamp

object EventsTable : LongIdTable("events") {
    val ownerId = long("owner_id").references(UsersTable.telegramId)
    val title = varchar("title", 256)
    val description = text("description").nullable()
    val location = varchar("location", 256).nullable()
    val startsAt = timestamp("starts_at")
    val capacity = integer("capacity").nullable()
    val status = enumerationByName("status", 16, EventStatus::class)
    val createdAt = timestamp("created_at")
}
