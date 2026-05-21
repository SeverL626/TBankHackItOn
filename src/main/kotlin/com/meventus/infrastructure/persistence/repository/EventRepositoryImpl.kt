package com.meventus.infrastructure.persistence.repository

import com.meventus.domain.model.Event
import com.meventus.domain.repository.EventRepository
import com.meventus.infrastructure.persistence.tables.EventsTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Instant

class EventRepositoryImpl : EventRepository {

    override fun findById(id: Long): Event? = transaction {
        EventsTable.selectAll()
            .where { EventsTable.id eq id }
            .map(::toEvent)
            .firstOrNull()
    }

    override fun findUpcoming(now: Instant): List<Event> = transaction {
        EventsTable.selectAll()
            .where { EventsTable.startsAt greaterEq now }
            .orderBy(EventsTable.startsAt, SortOrder.ASC)
            .map(::toEvent)
    }

    override fun findByOwner(ownerId: Long): List<Event> = transaction {
        EventsTable.selectAll()
            .where { EventsTable.ownerId eq ownerId }
            .map(::toEvent)
    }

    override fun save(event: Event): Event = transaction {
        if (event.id == 0L) {
            val id = EventsTable.insertAndGetId {
                it[ownerId] = event.ownerId
                it[title] = event.title
                it[description] = event.description
                it[location] = event.location
                it[startsAt] = event.startsAt
                it[capacity] = event.capacity
                it[status] = event.status
                it[createdAt] = event.createdAt
            }.value
            event.copy(id = id)
        } else {
            EventsTable.update({ EventsTable.id eq event.id }) {
                it[title] = event.title
                it[description] = event.description
                it[location] = event.location
                it[startsAt] = event.startsAt
                it[capacity] = event.capacity
                it[status] = event.status
            }
            event
        }
    }

    override fun delete(id: Long) {
        transaction { EventsTable.deleteWhere { EventsTable.id eq id } }
    }

    private fun toEvent(row: ResultRow): Event = Event(
        id = row[EventsTable.id].value,
        ownerId = row[EventsTable.ownerId],
        title = row[EventsTable.title],
        description = row[EventsTable.description],
        location = row[EventsTable.location],
        startsAt = row[EventsTable.startsAt],
        capacity = row[EventsTable.capacity],
        status = row[EventsTable.status],
        createdAt = row[EventsTable.createdAt],
    )
}
