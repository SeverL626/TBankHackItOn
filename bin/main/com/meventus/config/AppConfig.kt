package com.meventus.config

import com.typesafe.config.ConfigFactory
import java.io.File

data class AppConfig(
    val bot: BotConfig,
    val database: DatabaseConfig,
    val webApp: WebAppConfig,
) {
    companion object {
        fun load(): AppConfig {
            val raw = ConfigFactory.load()
            val dotEnv = loadDotEnv()
            return AppConfig(
                bot = BotConfig(
                    token = value(raw, dotEnv, "bot.token", "BOT_TOKEN"),
                    username = value(raw, dotEnv, "bot.username", "BOT_USERNAME"),
                ),
                database = DatabaseConfig(
                    jdbcUrl = value(raw, dotEnv, "database.jdbcUrl", "DATABASE_URL"),
                    username = value(raw, dotEnv, "database.username", "DATABASE_USER"),
                    password = value(raw, dotEnv, "database.password", "DATABASE_PASSWORD"),
                    maxPoolSize = raw.getInt("database.maxPoolSize"),
                ),
                webApp = WebAppConfig(
                    port = value(raw, dotEnv, "webapp.port", "WEBAPP_PORT").toInt(),
                    url = value(raw, dotEnv, "webapp.url", "WEBAPP_URL"),
                ),
            )
        }

        private fun loadDotEnv(): Map<String, String> {
            val file = File(".env")
            if (!file.isFile) return emptyMap()
            return file.readLines()
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("#") && it.contains("=") }
                .associate {
                    val key = it.substringBefore("=").trim()
                    val value = it.substringAfter("=").trim().trim('"', '\'')
                    key to value
                }
        }

        private fun value(
            raw: com.typesafe.config.Config,
            dotEnv: Map<String, String>,
            path: String,
            envName: String,
        ): String =
            System.getenv(envName)
                ?: dotEnv[envName]
                ?: if (raw.hasPath(path)) raw.getString(path) else ""
    }
}
