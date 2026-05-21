package com.meventus.bot.messages

object Messages {
    fun welcome(name: String): String =
        "Привет, $name! Я помогу организовать мероприятия. /help — список команд."

    const val HELP: String = """
Доступные команды:
/new — создать мероприятие
/events — посмотреть афишу
/my — мои события
/help — помощь
"""
}
