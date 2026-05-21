package com.meventus

import com.meventus.bot.MeventusBot
import com.meventus.config.AppConfig
import com.meventus.infrastructure.persistence.DatabaseFactory
import com.meventus.webapp.WebAppServer

fun main() {
    val config = AppConfig.load()

    DatabaseFactory.init(config.database)

    WebAppServer.start(config.webApp.port)

    MeventusBot(config).start()
}
