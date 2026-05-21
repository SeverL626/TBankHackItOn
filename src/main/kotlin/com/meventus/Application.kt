package com.meventus

import com.meventus.bot.MeventusBot
import com.meventus.config.AppConfig
import com.meventus.infrastructure.persistence.DatabaseFactory

fun main() {
    val config = AppConfig.load()

    DatabaseFactory.init(config.database)

    MeventusBot(config).start()
}
