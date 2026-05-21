package com.meventus.bot.states

import com.meventus.domain.model.EventTag

sealed interface UserState {
    data object Idle : UserState

    data object AwaitingEventTitle : UserState

    data class AwaitingEventShortDesc(
        val title: String,
    ) : UserState

    data class AwaitingEventDescription(
        val title: String,
        val shortDesc: String,
    ) : UserState

    data class AwaitingEventAddress(
        val title: String,
        val shortDesc: String,
        val description: String,
    ) : UserState

    data class AwaitingEventDate(
        val title: String,
        val shortDesc: String,
        val description: String,
        val address: String,
    ) : UserState

    data class AwaitingEventCost(
        val title: String,
        val shortDesc: String,
        val description: String,
        val address: String,
        val startsAt: String,
    ) : UserState

    data class AwaitingEventPhoto(
        val title: String,
        val shortDesc: String,
        val description: String,
        val address: String,
        val startsAt: String,
        val cost: Long,
    ) : UserState

    data class AwaitingEventTags(
        val title: String,
        val shortDesc: String,
        val description: String,
        val address: String,
        val startsAt: String,
        val cost: Long,
        val photoFileId: String?,
        val selectedTags: Set<EventTag> = emptySet(),
    ) : UserState
}
