package com.meventus.bot.messages

object Messages {
    fun welcome(name: String): String =
        "Привет, $name! Я помогу организовать мероприятия. /help — список команд."

    const val HELP: String = """
Доступные команды:
/new — создать мероприятие
/events — список мероприятий
/my — мои мероприятия
/stats — статистика сообщений и мини-приложение
/help — это сообщение
"""
}
