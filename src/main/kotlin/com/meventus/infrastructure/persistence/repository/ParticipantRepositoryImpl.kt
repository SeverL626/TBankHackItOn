package com.meventus.infrastructure.persistence.repository

import com.meventus.domain.model.Event
import com.meventus.domain.model.Participant
import com.meventus.domain.repository.ParticipantRepository
import com.meventus.infrastructure.persistence.tables.EventsTable
import com.meventus.infrastructure.persistence.tables.ParticipantsTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class ParticipantRepositoryImpl : ParticipantRepository {

    override fun add(participant: Participant): Participant = transaction {
        ParticipantsTable.insert {
            it[eventId] = participant.eventId
            it[userId] = participant.userId
            it[joinedAt] = participant.joinedAt
            it[contributed] = participant.contributed
        }
        participant
    }

    override fun remove(eventId: Long, userId: Long) = transaction {
        ParticipantsTable.deleteWhere {
            (ParticipantsTable.eventId eq eventId) and (ParticipantsTable.userId eq userId)
        }
        Unit
    }

    override fun findByEvent(eventId: Long): List<Participant> = transaction {
        ParticipantsTable.selectAll()
            .where { ParticipantsTable.eventId eq eventId }
            .map {
                Participant(
                    eventId = it[ParticipantsTable.eventId],
                    userId = it[ParticipantsTable.userId],
                    joinedAt = it[ParticipantsTable.joinedAt],
                    contributed = it[ParticipantsTable.contributed],
                )
            }
    }

    override fun findEventsByUser(userId: Long): List<Event> = transaction {
        val eventIds = ParticipantsTable.selectAll()
            .where { ParticipantsTable.userId eq userId }
            .map { it[ParticipantsTable.eventId] }
        if (eventIds.isEmpty()) return@transaction emptyList()
        val eventRepo = EventRepositoryImpl()
        eventIds.mapNotNull { eventRepo.findById(it) }
    }

    override fun isParticipant(eventId: Long, userId: Long): Boolean = transaction {
        ParticipantsTable.selectAll()
            .where { (ParticipantsTable.eventId eq eventId) and (ParticipantsTable.userId eq userId) }
            .count() > 0
    }

    override fun updateContribution(eventId: Long, userId: Long, amount: Long) = transaction {
        ParticipantsTable.update({
            (ParticipantsTable.eventId eq eventId) and (ParticipantsTable.userId eq userId)
        }) {
            it[contributed] = amount
        }
        Unit
    }
}
