package com.meventus.bot.handlers

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.newChatMembers
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode

class GroupLifecycleHandler(
    private val botUsername: String,
) {
    fun register(dispatcher: Dispatcher) {
        dispatcher.newChatMembers {
            val username = botUsername.removePrefix("@")
            val botWasAdded = newChatMembers.any { member ->
                member.isBot && member.username.equals(username, ignoreCase = true)
            }
            if (!botWasAdded) return@newChatMembers

            bot.sendMessage(
                chatId = ChatId.fromId(message.chat.id),
                text = """
                    Привет! Я Meventur — календарь мероприятий для этой группы.

                    Что можно делать здесь:
                    /gevents — события этой группы
                    /gnew — создать событие группы
                    /ginvite — пригласить участников в событие

                    *Важно:* всем участникам нужно один раз написать мне /start в личку. Тогда я смогу записывать их в мероприятия, приглашать по @username и отправлять личные уведомления.
                """.trimIndent(),
                parseMode = ParseMode.MARKDOWN,
            )
        }
    }
}
