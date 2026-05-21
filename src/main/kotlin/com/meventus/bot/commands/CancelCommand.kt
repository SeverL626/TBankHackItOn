package com.meventus.bot.commands

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.meventus.bot.states.StateStorage
import com.meventus.bot.states.UserState

class CancelCommand(private val stateStorage: StateStorage) : Command {
    override val name = "cancel"

    override fun register(dispatcher: Dispatcher) {
        dispatcher.command(name) {
            val userId = message.from?.id ?: return@command
            val state = stateStorage.get(userId)
            stateStorage.clear(userId)
            val reply = when (state) {
                is UserState.Idle -> "Нет активного действия для отмены."
                is UserState.AwaitingBroadcast -> "Рассылка отменена."
                is UserState.AwaitingPaymentPhone,
                is UserState.AwaitingPaymentName -> "Подтверждение оплаты отменено."
                else -> "Создание мероприятия отменено. Данные удалены."
            }
            bot.sendMessage(
                chatId = ChatId.fromId(message.chat.id),
                text = reply,
                parseMode = ParseMode.MARKDOWN,
            )
        }
    }
}
