package com.meventus.domain.repository

interface TechUpdateRepository {
    fun setEnabled(chatId: Long, enabled: Boolean)
    fun listEnabledChatIds(): List<Long>
    fun getState(key: String): String?
    fun setState(key: String, value: String)
}
