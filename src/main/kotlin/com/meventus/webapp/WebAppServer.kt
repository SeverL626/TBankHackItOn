package com.meventus.webapp

import com.meventus.bot.stats.StatsStorage
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

object WebAppServer {
    fun start(port: Int) {
        val server = HttpServer.create(InetSocketAddress(port), 0)

        server.createContext("/") { exchange ->
            val bytes = WebAppServer::class.java
                .getResourceAsStream("/webapp/index.html")
                ?.readBytes()
                ?: "<h1>Not found</h1>".toByteArray()
            exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        server.createContext("/api/stats") { exchange ->
            val userId = exchange.requestURI.query
                ?.split("&")
                ?.firstOrNull { it.startsWith("userId=") }
                ?.removePrefix("userId=")
                ?.toLongOrNull() ?: 0L
            val stats = StatsStorage.get(userId)
            val topWordJson = if (stats.topWord != null) "\"${stats.topWord}\"" else "null"
            val json = """{"messageCount":${stats.messageCount},"topWord":$topWordJson}"""
            val bytes = json.toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        server.executor = null
        server.start()
    }
}
