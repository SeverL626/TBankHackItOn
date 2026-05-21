package com.meventus.bot.stats

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private class UserStatsEntry {
    val messageCount = AtomicInteger(0)
    val wordFrequency = ConcurrentHashMap<String, AtomicInteger>()
}

data class UserStats(val messageCount: Int, val topWord: String?)

object StatsStorage {
    private val data = ConcurrentHashMap<Long, UserStatsEntry>()

    fun record(userId: Long, text: String) {
        val entry = data.getOrPut(userId) { UserStatsEntry() }
        entry.messageCount.incrementAndGet()
        text.lowercase()
            .split(Regex("[^а-яёa-z0-9]+"))
            .filter { it.length > 2 }
            .forEach { word -> entry.wordFrequency.getOrPut(word) { AtomicInteger(0) }.incrementAndGet() }
    }

    fun get(userId: Long): UserStats {
        val entry = data[userId] ?: return UserStats(0, null)
        val topWord = entry.wordFrequency.maxByOrNull { it.value.get() }?.key
        return UserStats(entry.messageCount.get(), topWord)
    }
}
