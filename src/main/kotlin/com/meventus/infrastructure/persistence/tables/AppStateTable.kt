package com.meventus.infrastructure.persistence.tables

import org.jetbrains.exposed.sql.Table

object AppStateTable : Table("app_state") {
    val key = varchar("key", 128)
    val value = text("value")

    override val primaryKey = PrimaryKey(key)
}
