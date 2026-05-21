package com.meventus.infrastructure.persistence.repository

import com.meventus.domain.model.Event
import com.meventus.domain.model.Participant
import com.meventus.domain.repository.ParticipantRepository
import com.meventus.infrastructure.persistence.tables.EventsTable
import com.meventus.infrastructure.persistence.tables.ParticipantsTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.and

class ParticipantRepositoryImpl : ParticipantRepository {

    override fun add(participant: Participant): Participant = transaction {
        ParticipantsTable.insert {
            it[eventId] = participant.eventId
            it[userId] = participant.userId
            it[joinedAt] = participant.joinedAt
        }
        participant
    }

    override fun remove(eventId: Long, userId: Long) {
        transaction {
            ParticipantsTable.deleteWhere {
                (ParticipantsTable.eventId eq eventId) and (ParticipantsTable.userId eq userId)
            }
        }
    }

    override fun listByEvent(eventId: Long): List<Participant> = transaction {
        ParticipantsTable.selectAll()
            .where { ParticipantsTable.eventId eq eventId }
            .map {
                Participant(
                    eventId = it[ParticipantsTable.eventId],
                    userId = it[ParticipantsTable.userId],
                    joinedAt = it[ParticipantsTable.joinedAt],
                )
            }
    }

    override fun listEventsByUser(userId: Long): List<Event> = transaction {
        (ParticipantsTable innerJoin EventsTable)
            .selectAll()
            .where { ParticipantsTable.userId eq userId }
            .map(::toEvent)
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
