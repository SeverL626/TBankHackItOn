package com.meventus.infrastructure.persistence.repository

import com.meventus.domain.repository.TechUpdateRepository
import com.meventus.infrastructure.persistence.tables.AppStateTable
import com.meventus.infrastructure.persistence.tables.TechUpdateSubscriptionsTable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

class TechUpdateRepositoryImpl : TechUpdateRepository {
    override fun setEnabled(chatId: Long, enabled: Boolean) = transaction {
        val exists = TechUpdateSubscriptionsTable.selectAll()
            .where { TechUpdateSubscriptionsTable.chatId eq chatId }
            .any()
        if (exists) {
            TechUpdateSubscriptionsTable.update({ TechUpdateSubscriptionsTable.chatId eq chatId }) {
                it[TechUpdateSubscriptionsTable.enabled] = enabled
                it[TechUpdateSubscriptionsTable.updatedAt] = Instant.now()
            }
        } else {
            TechUpdateSubscriptionsTable.insert {
                it[TechUpdateSubscriptionsTable.chatId] = chatId
                it[TechUpdateSubscriptionsTable.enabled] = enabled
                it[TechUpdateSubscriptionsTable.updatedAt] = Instant.now()
            }
        }
        Unit
    }

    override fun listEnabledChatIds(): List<Long> = transaction {
        TechUpdateSubscriptionsTable.selectAll()
            .where { TechUpdateSubscriptionsTable.enabled eq true }
            .map { it[TechUpdateSubscriptionsTable.chatId] }
    }

    override fun getState(key: String): String? = transaction {
        AppStateTable.selectAll()
            .where { AppStateTable.key eq key }
            .firstOrNull()
            ?.get(AppStateTable.value)
    }

    override fun setState(key: String, value: String) = transaction {
        val exists = AppStateTable.selectAll().where { AppStateTable.key eq key }.any()
        if (exists) {
            AppStateTable.update({ AppStateTable.key eq key }) {
                it[AppStateTable.value] = value
            }
        } else {
            AppStateTable.insert {
                it[AppStateTable.key] = key
                it[AppStateTable.value] = value
            }
        }
        Unit
    }
}
