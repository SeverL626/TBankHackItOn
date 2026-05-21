package com.meventus.config

import com.typesafe.config.ConfigFactory

data class AppConfig(
    val bot: BotConfig,
    val database: DatabaseConfig,
    val webApp: WebAppConfig,
) {
    companion object {
        fun load(): AppConfig {
            val raw = ConfigFactory.load()
            return AppConfig(
                bot = BotConfig(
                    token = raw.getString("bot.token"),
                    username = raw.getString("bot.username"),
                ),
                database = DatabaseConfig(
                    jdbcUrl = raw.getString("database.jdbcUrl"),
                    username = raw.getString("database.username"),
                    password = raw.getString("database.password"),
                    maxPoolSize = raw.getInt("database.maxPoolSize"),
                ),
                webApp = WebAppConfig(
                    port = raw.getInt("webapp.port"),
                    url = raw.getString("webapp.url"),
                ),
            )
        }
    }
}
