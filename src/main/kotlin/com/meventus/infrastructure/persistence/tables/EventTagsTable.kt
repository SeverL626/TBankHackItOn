package com.meventus.infrastructure.persistence.tables

import com.meventus.domain.model.EventTag
import org.jetbrains.exposed.sql.Table

object EventTagsTable : Table("event_tags") {
    val eventId = long("event_id").references(EventsTable.id)
    val tag = enumerationByName("tag", 16, EventTag::class)
    override val primaryKey = PrimaryKey(eventId, tag)
}
