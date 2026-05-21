package com.meventus.webapp

import com.meventus.bot.stats.StatsStorage
import com.meventus.domain.model.EventTag
import com.meventus.domain.service.EventService
import com.meventus.domain.service.ParticipantService
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object WebAppServer {

    private val dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .withZone(ZoneId.of("UTC"))

    private var botToken: String = ""

    fun start(port: Int, eventService: EventService, participantService: ParticipantService, token: String) {
        botToken = token
        val server = HttpServer.create(InetSocketAddress(port), 0)

        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            when {
                path == "/" || path == "/index.html" -> serveHtml(exchange)
                else -> send404(exchange)
            }
        }

        server.createContext("/api/stats") { exchange ->
            val params = parseQuery(exchange.requestURI.query)
            val userId = params["userId"]?.toLongOrNull() ?: 0L
            val stats = StatsStorage.get(userId)
            val topWordJson = if (stats.topWord != null) "\"${stats.topWord}\"" else "null"
            sendJson(exchange, """{"messageCount":${stats.messageCount},"topWord":$topWordJson}""")
        }

        // GET /api/events?userId=X&tags=N&search=text
        server.createContext("/api/events") { exchange ->
            if (exchange.requestMethod == "OPTIONS") { sendCors(exchange); return@createContext }
            val params = parseQuery(exchange.requestURI.query)
            val userId = params["userId"]?.toLongOrNull() ?: 0L
            val tagBitmask = params["tags"]?.toIntOrNull() ?: 0
            val search = params["search"]?.lowercase() ?: ""

            val tags = EventTag.entries.filter { tagBitmask and it.bit != 0 }.toSet()
            val events = if (tags.isEmpty()) eventService.listUpcoming() else eventService.listByTags(tags)

            val filtered = if (search.isBlank()) events
            else events.filter {
                it.title.lowercase().contains(search) ||
                    it.shortDescription.lowercase().contains(search) ||
                    it.address.lowercase().contains(search)
            }

            val json = buildString {
                append("[")
                filtered.forEachIndexed { i, e ->
                    if (i > 0) append(",")
                    val isParticipant = userId > 0 && participantService.isParticipant(e.id, userId)
                    val participants = participantService.listByEvent(e.id)
                    val tagJson = e.tags.joinToString(",", "[", "]") { "\"${it.name}\"" }
                    val photoUrl = e.photoFileId?.let { "\"/api/photo?fileId=${it}\"" } ?: "null"
                    append("""{"id":${e.id},"title":${e.title.jsonStr()},"shortDescription":${e.shortDescription.jsonStr()},"address":${e.address.jsonStr()},"startsAt":"${dtf.format(e.startsAt)}","cost":${e.cost},"tags":$tagJson,"participantCount":${participants.size},"isParticipant":$isParticipant,"ownerId":${e.ownerId},"status":"${e.status.name}","photoUrl":$photoUrl}""")
                }
                append("]")
            }
            sendJson(exchange, json)
        }

        // GET /api/event?id=X&userId=X
        server.createContext("/api/event") { exchange ->
            if (exchange.requestMethod == "OPTIONS") { sendCors(exchange); return@createContext }
            val params = parseQuery(exchange.requestURI.query)
            val id = params["id"]?.toLongOrNull() ?: run { send404(exchange); return@createContext }
            val userId = params["userId"]?.toLongOrNull() ?: 0L
            val event = eventService.findById(id) ?: run { send404(exchange); return@createContext }
            val isParticipant = userId > 0 && participantService.isParticipant(id, userId)
            val participants = participantService.listByEvent(id)
            val totalContrib = participants.sumOf { it.contributed }
            val tagJson = event.tags.joinToString(",", "[", "]") { "\"${it.name}\"" }
            val photoUrl = event.photoFileId?.let { "\"/api/photo?fileId=${it}\"" } ?: "null"
            val json = """{"id":${event.id},"title":${event.title.jsonStr()},"shortDescription":${event.shortDescription.jsonStr()},"description":${event.description.jsonStr()},"address":${event.address.jsonStr()},"startsAt":"${dtf.format(event.startsAt)}","cost":${event.cost},"tags":$tagJson,"participantCount":${participants.size},"totalContributed":$totalContrib,"isParticipant":$isParticipant,"ownerId":${event.ownerId},"status":"${event.status.name}","photoUrl":$photoUrl,"paymentType":"${event.paymentType.name}","sbpPhone":${event.sbpPhone?.jsonStr() ?: "null"},"sbpName":${event.sbpName?.jsonStr() ?: "null"}}"""
            sendJson(exchange, json)
        }

        // POST /api/join  body: eventId=X&userId=X
        server.createContext("/api/join") { exchange ->
            if (exchange.requestMethod == "OPTIONS") { sendCors(exchange); return@createContext }
            val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            val params = parseQuery(body)
            val eventId = params["eventId"]?.toLongOrNull() ?: run { sendErr(exchange, "bad eventId"); return@createContext }
            val userId = params["userId"]?.toLongOrNull() ?: run { sendErr(exchange, "bad userId"); return@createContext }
            val event = eventService.findById(eventId)
            if (event != null && event.ownerId == userId) {
                sendJson(exchange, """{"ok":false,"error":"owner cannot join own event"}""")
                return@createContext
            }
            if (participantService.isParticipant(eventId, userId)) {
                sendJson(exchange, """{"ok":false,"error":"already joined"}""")
                return@createContext
            }
            if (event != null && event.paymentType == com.meventus.domain.model.PaymentType.ADVANCE) {
                val costStr = if (event.cost > 0) "${event.cost}₽" else "бесплатно"
                val text = "💳 *Оплата заранее через СБП*\n\nМероприятие: *${event.title}*\nСумма: $costStr\nНомер: `${event.sbpPhone}`\nПолучатель: *${event.sbpName}*\n\nПосле перевода нажмите кнопку ниже — организатор получит уведомление и подтвердит оплату."
                val keyboard = """{"inline_keyboard":[[{"text":"📲 Я перевёл деньги","callback_data":"pay_sent:${event.id}"}]]}"""
                sendTelegramMessage(userId, text, keyboard)
                sendJson(exchange, """{"ok":true,"paymentRequired":true}""")
                return@createContext
            }
            participantService.join(eventId, userId)
            sendJson(exchange, """{"ok":true}""")
        }

        // POST /api/leave  body: eventId=X&userId=X
        server.createContext("/api/leave") { exchange ->
            if (exchange.requestMethod == "OPTIONS") { sendCors(exchange); return@createContext }
            val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            val params = parseQuery(body)
            val eventId = params["eventId"]?.toLongOrNull() ?: run { sendErr(exchange, "bad eventId"); return@createContext }
            val userId = params["userId"]?.toLongOrNull() ?: run { sendErr(exchange, "bad userId"); return@createContext }
            participantService.leave(eventId, userId)
            sendJson(exchange, """{"ok":true}""")
        }

        // GET /api/admin?userId=X — events owned by user with participant + contribution stats
        server.createContext("/api/admin") { exchange ->
            if (exchange.requestMethod == "OPTIONS") { sendCors(exchange); return@createContext }
            val params = parseQuery(exchange.requestURI.query)
            val userId = params["userId"]?.toLongOrNull() ?: run { sendErr(exchange, "bad userId"); return@createContext }
            val events = eventService.listByOwner(userId)
            val json = buildString {
                append("[")
                events.forEachIndexed { i, e ->
                    if (i > 0) append(",")
                    val participants = participantService.listByEvent(e.id)
                    val totalContrib = participants.sumOf { it.contributed }
                    val tagJson = e.tags.joinToString(",", "[", "]") { "\"${it.name}\"" }
                    append("""{"id":${e.id},"title":${e.title.jsonStr()},"address":${e.address.jsonStr()},"startsAt":"${dtf.format(e.startsAt)}","cost":${e.cost},"tags":$tagJson,"participantCount":${participants.size},"totalContributed":$totalContrib,"status":"${e.status.name}"}""")
                }
                append("]")
            }
            sendJson(exchange, json)
        }

        // POST /api/event/update  body: id=X&userId=X&title=...&shortDescription=...&description=...&address=...&startsAt=YYYY-MM-DDTHH:MM&cost=N&tags=N
        server.createContext("/api/event/update") { exchange ->
            if (exchange.requestMethod == "OPTIONS") { sendCors(exchange); return@createContext }
            val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            val params = parseQuery(body)
            val id = params["id"]?.toLongOrNull() ?: run { sendErr(exchange, "bad id"); return@createContext }
            val userId = params["userId"]?.toLongOrNull() ?: run { sendErr(exchange, "bad userId"); return@createContext }
            val title = params["title"]?.takeIf { it.isNotBlank() } ?: run { sendErr(exchange, "bad title"); return@createContext }
            val shortDescription = params["shortDescription"] ?: ""
            val description = params["description"] ?: ""
            val address = params["address"] ?: ""
            val startsAtStr = params["startsAt"] ?: run { sendErr(exchange, "bad startsAt"); return@createContext }
            val cost = params["cost"]?.toLongOrNull() ?: 0L
            val tagBitmask = params["tags"]?.toIntOrNull() ?: 0
            val tags = EventTag.entries.filter { tagBitmask and it.bit != 0 }.toSet()

            val startsAt = runCatching {
                // accepts "YYYY-MM-DDTHH:MM" from datetime-local input
                val ldt = LocalDateTime.parse(startsAtStr.take(16), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
                ldt.toInstant(ZoneOffset.UTC)
            }.getOrNull() ?: run { sendErr(exchange, "bad startsAt format"); return@createContext }

            val updated = eventService.update(id, userId, title, shortDescription, description, address, startsAt, cost, tags)
            if (updated == null) {
                sendJson(exchange, """{"ok":false,"error":"not found or not owner"}""")
            } else {
                sendJson(exchange, """{"ok":true,"id":${updated.id}}""")
            }
        }

        // GET /api/photo?fileId=X — proxy Telegram file to browser
        server.createContext("/api/photo") { exchange ->
            val params = parseQuery(exchange.requestURI.query)
            val fileId = params["fileId"] ?: run { send404(exchange); return@createContext }
            runCatching {
                val getFileJson = URL("https://api.telegram.org/bot$botToken/getFile?file_id=$fileId").readText()
                val filePath = """"file_path"\s*:\s*"([^"]+)"""".toRegex().find(getFileJson)?.groupValues?.get(1)
                    ?: run { send404(exchange); return@createContext }
                val bytes = URL("https://api.telegram.org/file/bot$botToken/$filePath").readBytes()
                val contentType = if (filePath.endsWith(".png")) "image/png" else "image/jpeg"
                exchange.responseHeaders.set("Content-Type", contentType)
                exchange.responseHeaders.set("Cache-Control", "max-age=86400")
                exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }.onFailure { send404(exchange) }
        }

        server.executor = null
        server.start()
        println("WebApp started on port $port")
    }

    private fun serveHtml(exchange: HttpExchange) {
        val bytes = WebAppServer::class.java
            .getResourceAsStream("/webapp/index.html")
            ?.readBytes()
            ?: "<h1>Not found</h1>".toByteArray()
        exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
        exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun sendJson(exchange: HttpExchange, json: String) {
        val bytes = json.toByteArray()
        exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun sendErr(exchange: HttpExchange, msg: String) {
        sendJson(exchange, """{"ok":false,"error":"$msg"}""")
    }

    private fun send404(exchange: HttpExchange) {
        val bytes = "Not found".toByteArray()
        exchange.sendResponseHeaders(404, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun sendCors(exchange: HttpExchange) {
        exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
        exchange.responseHeaders.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        exchange.responseHeaders.set("Access-Control-Allow-Headers", "Content-Type")
        exchange.sendResponseHeaders(204, -1)
    }

    private fun sendTelegramMessage(chatId: Long, text: String, replyMarkup: String? = null) {
        runCatching {
            val encodedText = java.net.URLEncoder.encode(text, "UTF-8")
            var urlStr = "https://api.telegram.org/bot$botToken/sendMessage?chat_id=$chatId&text=$encodedText&parse_mode=Markdown"
            if (replyMarkup != null) urlStr += "&reply_markup=${java.net.URLEncoder.encode(replyMarkup, "UTF-8")}"
            URL(urlStr).readText()
        }
    }

    private fun parseQuery(query: String?): Map<String, String> =
        query?.split("&")
            ?.mapNotNull {
                val idx = it.indexOf('=')
                if (idx < 0) null else it.substring(0, idx) to java.net.URLDecoder.decode(it.substring(idx + 1), "UTF-8")
            }
            ?.toMap() ?: emptyMap()

    private fun String.jsonStr(): String {
        val escaped = this
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }
}
