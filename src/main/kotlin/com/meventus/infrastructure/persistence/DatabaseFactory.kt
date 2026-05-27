package com.meventus.infrastructure.persistence

import com.meventus.config.DatabaseConfig
import com.meventus.infrastructure.persistence.tables.AppStateTable
import com.meventus.infrastructure.persistence.tables.CustomRemindersTable
import com.meventus.infrastructure.persistence.tables.EventTagsTable
import com.meventus.infrastructure.persistence.tables.EventsTable
import com.meventus.infrastructure.persistence.tables.ParticipantsTable
import com.meventus.infrastructure.persistence.tables.TechUpdateSubscriptionsTable
import com.meventus.infrastructure.persistence.tables.UsersTable
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    fun init(config: DatabaseConfig) {
        val hikari = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.jdbcUrl
                username = config.username
                password = config.password
                maximumPoolSize = config.maxPoolSize
                driverClassName = "org.postgresql.Driver"
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                validate()
            },
        )
        Database.connect(hikari)
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                UsersTable,
                EventsTable,
                EventTagsTable,
                ParticipantsTable,
                TechUpdateSubscriptionsTable,
                AppStateTable,
                CustomRemindersTable,
            )
        }
    }
}
