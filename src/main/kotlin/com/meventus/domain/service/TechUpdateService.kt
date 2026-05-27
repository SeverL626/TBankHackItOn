package com.meventus.domain.service

import com.meventus.domain.repository.TechUpdateRepository

class TechUpdateService(
    private val techUpdateRepository: TechUpdateRepository,
) {
    fun enable(chatId: Long) = techUpdateRepository.setEnabled(chatId, true)

    fun disable(chatId: Long) = techUpdateRepository.setEnabled(chatId, false)

    fun enabledChatIds(): List<Long> = techUpdateRepository.listEnabledChatIds()

    fun shouldNotifyRelease(releaseId: String): Boolean {
        val last = techUpdateRepository.getState(LAST_RELEASE_KEY)
        if (last == releaseId) return false
        techUpdateRepository.setState(LAST_RELEASE_KEY, releaseId)
        return true
    }

    companion object {
        private const val LAST_RELEASE_KEY = "last_notified_release"
    }
}
