package com.meventus.infrastructure.persistence.repository

import com.meventus.domain.model.Event
import com.meventus.domain.model.EventTag
import com.meventus.domain.model.Participant
import com.meventus.domain.model.PaymentStatus
import com.meventus.domain.repository.ParticipantRepository
import com.meventus.infrastructure.persistence.tables.EventTagsTable
import com.meventus.infrastructure.persistence.tables.EventsTable
import com.meventus.infrastructure.persistence.tables.ParticipantsTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.innerJoin
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
            it[paymentStatus] = participant.paymentStatus
            it[payerPhone] = participant.payerPhone
            it[payerName] = participant.payerName
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
            .map { toParticipant(it) }
    }

    override fun findParticipant(eventId: Long, userId: Long): Participant? = transaction {
        ParticipantsTable.selectAll()
            .where { (ParticipantsTable.eventId eq eventId) and (ParticipantsTable.userId eq userId) }
            .limit(1)
            .firstOrNull()
            ?.let { toParticipant(it) }
    }

    // 2 queries total: JOIN for events, batch select for tags
    override fun findEventsByUser(userId: Long): List<Event> = transaction {
        val rows = (ParticipantsTable innerJoin EventsTable)
            .selectAll()
            .where { ParticipantsTable.userId eq userId }
            .toList()

        if (rows.isEmpty()) return@transaction emptyList()

        val eventIds = rows.map { it[EventsTable.id].value }.toSet()
        val tagMap: Map<Long, Set<EventTag>> = EventTagsTable
            .selectAll()
            .where { EventTagsTable.eventId inList eventIds.toList() }
            .groupBy({ it[EventTagsTable.eventId] }, { it[EventTagsTable.tag] })
            .mapValues { it.value.toSet() }

        rows.map { row ->
            val id = row[EventsTable.id].value
            toEvent(row, tagMap[id] ?: emptySet())
        }
    }

    override fun isParticipant(eventId: Long, userId: Long): Boolean = transaction {
        ParticipantsTable.selectAll()
            .where { (ParticipantsTable.eventId eq eventId) and (ParticipantsTable.userId eq userId) }
            .limit(1)
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

    override fun updatePaymentStatus(eventId: Long, userId: Long, status: PaymentStatus, contributed: Long?) = transaction {
        ParticipantsTable.update({
            (ParticipantsTable.eventId eq eventId) and (ParticipantsTable.userId eq userId)
        }) {
            it[paymentStatus] = status
            if (contributed != null) it[ParticipantsTable.contributed] = contributed
        }
        Unit
    }

    private fun toParticipant(row: ResultRow) = Participant(
        eventId = row[ParticipantsTable.eventId],
        userId = row[ParticipantsTable.userId],
        joinedAt = row[ParticipantsTable.joinedAt],
        contributed = row[ParticipantsTable.contributed],
        paymentStatus = row[ParticipantsTable.paymentStatus],
        payerPhone = row[ParticipantsTable.payerPhone],
        payerName = row[ParticipantsTable.payerName],
    )

    private fun toEvent(row: ResultRow, tags: Set<EventTag>) = Event(
        id = row[EventsTable.id].value,
        ownerId = row[EventsTable.ownerId],
        title = row[EventsTable.title],
        shortDescription = row[EventsTable.shortDescription],
        description = row[EventsTable.description],
        photoFileId = row[EventsTable.photoFileId],
        tags = tags,
        address = row[EventsTable.address],
        startsAt = row[EventsTable.startsAt],
        cost = row[EventsTable.cost],
        status = row[EventsTable.status],
        createdAt = row[EventsTable.createdAt],
        paymentType = row[EventsTable.paymentType],
        sbpPhone = row[EventsTable.sbpPhone],
        sbpName = row[EventsTable.sbpName],
        visibility = row[EventsTable.visibility],
        registrationMode = row[EventsTable.registrationMode],
        groupChatId = row[EventsTable.groupChatId],
    )
}
