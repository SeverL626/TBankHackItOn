package com.meventus.domain.model

enum class EventTag(val displayName: String, val emoji: String, val bit: Int) {
    IT("IT", "💻", 1),
    SPORT("Спорт", "⚽", 2),
    OUTDOORS("На улице", "🏕", 4),
    INDOORS("В помещении", "🏠", 8),
}

fun bitmaskToTags(bitmask: Int): Set<EventTag> =
    EventTag.values().filter { bitmask and it.bit != 0 }.toSet()

fun Set<EventTag>.toBitmask(): Int = fold(0) { acc, tag -> acc or tag.bit }
