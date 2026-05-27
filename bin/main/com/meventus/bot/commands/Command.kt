package com.meventus.bot.commands

import com.github.kotlintelegrambot.dispatcher.Dispatcher

interface Command {
    val name: String
    fun register(dispatcher: Dispatcher)
}
