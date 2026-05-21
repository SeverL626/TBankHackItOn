package com.meventus.bot.states

sealed interface UserState {
    data object Idle : UserState
    data object AwaitingEventTitle : UserState
    data class AwaitingEventDate(val title: String) : UserState
    data class AwaitingEventLocation(val title: String, val startsAt: String) : UserState
}
