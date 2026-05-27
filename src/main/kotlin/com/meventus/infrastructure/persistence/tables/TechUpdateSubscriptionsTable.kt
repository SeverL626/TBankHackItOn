package com.meventus.infrastructure.persistence.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object TechUpdateSubscriptionsTable : Table("tech_update_subscriptions") {
    val chatId = long("chat_id")
    val enabled = bool("enabled").default(true)
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(chatId)
}
