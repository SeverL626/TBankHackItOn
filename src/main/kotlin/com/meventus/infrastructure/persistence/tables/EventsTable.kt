package com.meventus.infrastructure.persistence.tables

import com.meventus.domain.model.EventStatus
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestamp

object EventsTable : LongIdTable("events") {
    val ownerId = long("owner_id").references(UsersTable.telegramId)
    val title = varchar("title", 256)
    val shortDescription = varchar("short_description", 512).default("")
    val description = text("description").default("")
    val photoFileId = varchar("photo_file_id", 256).nullable()
    val address = varchar("address", 512).default("")
    val startsAt = timestamp("starts_at")
    val cost = long("cost").default(0)
    val status = enumerationByName("status", 16, EventStatus::class)
    val createdAt = timestamp("created_at")
}
