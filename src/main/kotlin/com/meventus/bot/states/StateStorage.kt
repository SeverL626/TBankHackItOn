package com.meventus.bot.states

interface StateStorage {
    fun get(userId: Long): UserState
    fun set(userId: Long, state: UserState)
    fun clear(userId: Long)
}

class InMemoryStateStorage : StateStorage {
    private val states = mutableMapOf<Long, UserState>()

    override fun get(userId: Long): UserState = states[userId] ?: UserState.Idle
    override fun set(userId: Long, state: UserState) { states[userId] = state }
    override fun clear(userId: Long) { states.remove(userId) }
}
