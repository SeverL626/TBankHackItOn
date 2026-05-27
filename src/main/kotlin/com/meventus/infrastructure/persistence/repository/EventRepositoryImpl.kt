package com.meventus.infrastructure.persistence.repository

import com.meventus.domain.model.Event
import com.meventus.domain.model.EventRegistrationMode
import com.meventus.domain.model.EventStatus
import com.meventus.domain.model.EventTag
import com.meventus.domain.model.EventVisibility
import com.meventus.domain.model.PaymentType
import com.meventus.domain.repository.EventRepository
import com.meventus.infrastructure.persistence.tables.CustomRemindersTable
import com.meventus.infrastructure.persistence.tables.EventTagsTable
import com.meventus.infrastructure.persistence.tables.EventsTable
import com.meventus.infrastructure.persistence.tables.ParticipantsTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

class EventRepositoryImpl : EventRepository {

    override fun findById(id: Long): Event? = transaction {
        val row = EventsTable.selectAll().where { EventsTable.id eq id }.firstOrNull()
            ?: return@transaction null
        val tags = loadTags(setOf(id))[id] ?: emptySet()
        toEvent(row, tags)
    }

    override fun findUpcoming(now: Instant): List<Event> = transaction {
            val rows = EventsTable.selectAll()
            .where {
                (EventsTable.startsAt greaterEq now) and
                    (EventsTable.visibility eq EventVisibility.PUBLIC) and
                    (EventsTable.status eq EventStatus.PUBLISHED)
            }
            .orderBy(EventsTable.startsAt, SortOrder.ASC)
            .toList()
        attachTags(rows)
    }

    override fun findUpcomingAll(now: Instant): List<Event> = transaction {
        val rows = EventsTable.selectAll()
            .where {
                (EventsTable.startsAt greaterEq now) and
                    (EventsTable.status eq EventStatus.PUBLISHED)
            }
            .orderBy(EventsTable.startsAt, SortOrder.ASC)
            .toList()
        attachTags(rows)
    }

    override fun findByTags(tags: Set<EventTag>, now: Instant): List<Event> = transaction {
        if (tags.isEmpty()) return@transaction findUpcoming(now)
        val matchingIds = EventTagsTable
            .select(EventTagsTable.eventId)
            .where { EventTagsTable.tag inList tags.toList() }
            .map { it[EventTagsTable.eventId] }
            .toSet()
        if (matchingIds.isEmpty()) return@transaction emptyList()
        val rows = EventsTable.selectAll()
            .where {
                    (EventsTable.id inList matchingIds.toList()) and
                    (EventsTable.startsAt greaterEq now) and
                    (EventsTable.visibility eq EventVisibility.PUBLIC) and
                    (EventsTable.status eq EventStatus.PUBLISHED)
            }
            .orderBy(EventsTable.startsAt, SortOrder.ASC)
            .toList()
        attachTags(rows)
    }

    override fun findByOwner(ownerId: Long): List<Event> = transaction {
        val rows = EventsTable.selectAll()
            .where { EventsTable.ownerId eq ownerId }
            .toList()
        attachTags(rows)
    }

    override fun findByGroup(groupChatId: Long, now: Instant): List<Event> = transaction {
        val rows = EventsTable.selectAll()
            .where {
                (EventsTable.groupChatId eq groupChatId) and
                    (EventsTable.startsAt greaterEq now) and
                    (EventsTable.status eq EventStatus.PUBLISHED)
            }
            .orderBy(EventsTable.startsAt, SortOrder.ASC)
            .toList()
        attachTags(rows)
    }

    override fun save(event: Event): Event = transaction {
        if (event.id == 0L) {
            val id = EventsTable.insertAndGetId {
                it[ownerId] = event.ownerId
                it[title] = event.title
                it[shortDescription] = event.shortDescription
                it[description] = event.description
                it[photoFileId] = event.photoFileId
                it[address] = event.address
                it[startsAt] = event.startsAt
                it[cost] = event.cost
                it[status] = event.status
                it[createdAt] = event.createdAt
                it[paymentType] = event.paymentType
                it[sbpPhone] = event.sbpPhone
                it[sbpName] = event.sbpName
                it[visibility] = event.visibility
                it[registrationMode] = event.registrationMode
                it[groupChatId] = event.groupChatId
            }.value
            saveTags(id, event.tags)
            event.copy(id = id)
        } else {
            EventsTable.update({ EventsTable.id eq event.id }) {
                it[title] = event.title
                it[shortDescription] = event.shortDescription
                it[description] = event.description
                it[photoFileId] = event.photoFileId
                it[address] = event.address
                it[startsAt] = event.startsAt
                it[cost] = event.cost
                it[status] = event.status
                it[paymentType] = event.paymentType
                it[sbpPhone] = event.sbpPhone
                it[sbpName] = event.sbpName
                it[visibility] = event.visibility
                it[registrationMode] = event.registrationMode
                it[groupChatId] = event.groupChatId
            }
            EventTagsTable.deleteWhere { eventId eq event.id }
            saveTags(event.id, event.tags)
            event
        }
    }


    override fun delete(id: Long) = transaction {
        CustomRemindersTable.deleteWhere { eventId eq id }
        ParticipantsTable.deleteWhere { eventId eq id }
        EventTagsTable.deleteWhere { eventId eq id }
        EventsTable.deleteWhere { EventsTable.id eq id }
        Unit
    }

    private fun saveTags(eventId: Long, tags: Set<EventTag>) {
        tags.forEach { tag ->
            EventTagsTable.insert {
                it[EventTagsTable.eventId] = eventId
                it[EventTagsTable.tag] = tag
            }
        }
    }

    private fun loadTags(eventIds: Set<Long>): Map<Long, Set<EventTag>> {
        if (eventIds.isEmpty()) return emptyMap()
        return EventTagsTable.selectAll()
            .where { EventTagsTable.eventId inList eventIds.toList() }
            .groupBy({ it[EventTagsTable.eventId] }, { it[EventTagsTable.tag] })
            .mapValues { it.value.toSet() }
    }

    private fun attachTags(rows: List<ResultRow>): List<Event> {
        val ids = rows.map { it[EventsTable.id].value }.toSet()
        val tagMap = loadTags(ids)
        return rows.map { toEvent(it, tagMap[it[EventsTable.id].value] ?: emptySet()) }
    }

    private fun toEvent(row: ResultRow, tags: Set<EventTag>): Event = Event(
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
