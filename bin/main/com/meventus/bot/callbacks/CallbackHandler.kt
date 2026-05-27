package com.meventus.bot.callbacks

import com.github.kotlintelegrambot.dispatcher.Dispatcher

interface CallbackHandler {
    val prefix: String
    fun register(dispatcher: Dispatcher)
}
