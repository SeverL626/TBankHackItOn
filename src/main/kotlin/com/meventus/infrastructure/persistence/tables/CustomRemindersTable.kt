package com.meventus.infrastructure.persistence.tables

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestamp

object CustomRemindersTable : LongIdTable("custom_reminders") {
    val eventId = long("event_id").references(EventsTable.id)
    val secondsBefore = long("seconds_before")
    val message = text("message")
    val sent = bool("sent").default(false)
    val createdAt = timestamp("created_at")
}
