package com.meventus.infrastructure.persistence.repository

import com.meventus.domain.model.CustomReminder
import com.meventus.domain.repository.CustomReminderRepository
import com.meventus.infrastructure.persistence.tables.CustomRemindersTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class CustomReminderRepositoryImpl : CustomReminderRepository {
    override fun save(reminder: CustomReminder): CustomReminder = transaction {
        val id = CustomRemindersTable.insertAndGetId {
            it[CustomRemindersTable.eventId] = reminder.eventId
            it[CustomRemindersTable.secondsBefore] = reminder.secondsBefore
            it[CustomRemindersTable.message] = reminder.message
            it[CustomRemindersTable.sent] = reminder.sent
            it[CustomRemindersTable.createdAt] = reminder.createdAt
        }.value
        reminder.copy(id = id)
    }

    override fun findPending(): List<CustomReminder> = transaction {
        CustomRemindersTable.selectAll()
            .where { CustomRemindersTable.sent eq false }
            .map(::toReminder)
    }

    override fun markSent(id: Long) = transaction {
        CustomRemindersTable.update({ CustomRemindersTable.id eq id }) {
            it[sent] = true
        }
        Unit
    }

    override fun deleteByEvent(eventId: Long) = transaction {
        CustomRemindersTable.deleteWhere { CustomRemindersTable.eventId eq eventId }
        Unit
    }

    private fun toReminder(row: ResultRow): CustomReminder =
        CustomReminder(
            id = row[CustomRemindersTable.id].value,
            eventId = row[CustomRemindersTable.eventId],
            secondsBefore = row[CustomRemindersTable.secondsBefore],
            message = row[CustomRemindersTable.message],
            sent = row[CustomRemindersTable.sent],
            createdAt = row[CustomRemindersTable.createdAt],
        )
}
