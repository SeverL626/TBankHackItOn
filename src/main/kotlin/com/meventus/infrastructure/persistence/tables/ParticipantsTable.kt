package com.meventus.infrastructure.persistence.tables

import com.meventus.domain.model.PaymentStatus
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object ParticipantsTable : Table("participants") {
    val eventId = long("event_id").references(EventsTable.id)
    val userId = long("user_id").references(UsersTable.telegramId)
    val joinedAt = timestamp("joined_at")
    val contributed = long("contributed").default(0)
    val paymentStatus = enumerationByName("payment_status", 16, PaymentStatus::class).default(PaymentStatus.NOT_REQUIRED)
    val payerPhone = varchar("payer_phone", 32).nullable()
    val payerName = varchar("payer_name", 128).nullable()
    override val primaryKey = PrimaryKey(eventId, userId)
}
