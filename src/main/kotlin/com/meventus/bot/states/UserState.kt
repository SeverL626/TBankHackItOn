package com.meventus.bot.states

import com.meventus.domain.model.EventTag
import com.meventus.domain.model.EventVisibility
import com.meventus.domain.model.PaymentType

sealed interface UserState {
    data object Idle : UserState

    data class AwaitingEventTitle(val visibility: EventVisibility = EventVisibility.PUBLIC) : UserState

    data class AwaitingEventShortDesc(val title: String, val visibility: EventVisibility = EventVisibility.PUBLIC) : UserState

    data class AwaitingEventDescription(val title: String, val shortDesc: String, val visibility: EventVisibility = EventVisibility.PUBLIC) : UserState

    data class AwaitingEventAddress(
        val title: String, val shortDesc: String, val description: String, val visibility: EventVisibility = EventVisibility.PUBLIC,
    ) : UserState

    data class AwaitingEventDate(
        val title: String, val shortDesc: String, val description: String, val address: String, val visibility: EventVisibility = EventVisibility.PUBLIC,
    ) : UserState

    data class AwaitingEventCost(
        val title: String, val shortDesc: String, val description: String,
        val address: String, val startsAt: String, val visibility: EventVisibility = EventVisibility.PUBLIC,
    ) : UserState

    // After cost: user picks payment type via inline keyboard
    data class AwaitingEventPaymentType(
        val title: String, val shortDesc: String, val description: String,
        val address: String, val startsAt: String, val cost: Long, val visibility: EventVisibility = EventVisibility.PUBLIC,
    ) : UserState

    // If advance payment: collect SBP details
    data class AwaitingEventSbpPhone(
        val title: String, val shortDesc: String, val description: String,
        val address: String, val startsAt: String, val cost: Long, val visibility: EventVisibility = EventVisibility.PUBLIC,
    ) : UserState

    data class AwaitingEventSbpName(
        val title: String, val shortDesc: String, val description: String,
        val address: String, val startsAt: String, val cost: Long,
        val sbpPhone: String, val visibility: EventVisibility = EventVisibility.PUBLIC,
    ) : UserState

    // Photo step — now carries payment info decided earlier
    data class AwaitingEventPhoto(
        val title: String, val shortDesc: String, val description: String,
        val address: String, val startsAt: String, val cost: Long,
        val paymentType: PaymentType = PaymentType.ON_SITE,
        val sbpPhone: String? = null,
        val sbpName: String? = null,
        val visibility: EventVisibility = EventVisibility.PUBLIC,
    ) : UserState

    // Tags step — carries all data including payment
    data class AwaitingEventTags(
        val title: String, val shortDesc: String, val description: String,
        val address: String, val startsAt: String, val cost: Long,
        val photoFileId: String?,
        val selectedTags: Set<EventTag> = emptySet(),
        val paymentType: PaymentType = PaymentType.ON_SITE,
        val sbpPhone: String? = null,
        val sbpName: String? = null,
        val visibility: EventVisibility = EventVisibility.PUBLIC,
    ) : UserState

    data class AwaitingBroadcast(val eventId: Long) : UserState

    // Joining — payment confirmation
    data class AwaitingPaymentPhone(val eventId: Long) : UserState
    data class AwaitingPaymentName(val eventId: Long, val phone: String) : UserState
}
