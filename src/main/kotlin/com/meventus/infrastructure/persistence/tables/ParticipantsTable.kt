package com.meventus.infrastructure.persistence.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object ParticipantsTable : Table("participants") {
    val eventId = long("event_id").references(EventsTable.id)
    val userId = long("user_id").references(UsersTable.telegramId)
    val joinedAt = timestamp("joined_at")
    val contributed = long("contributed").default(0)
    override val primaryKey = PrimaryKey(eventId, userId)
}
