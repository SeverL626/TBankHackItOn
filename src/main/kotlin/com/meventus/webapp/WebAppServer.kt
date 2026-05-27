package com.meventus.webapp

import com.meventus.bot.stats.StatsStorage
import com.meventus.domain.model.EventRegistrationMode
import com.meventus.domain.model.EventTag
import com.meventus.domain.model.EventVisibility
import com.meventus.domain.model.PaymentType
import com.meventus.domain.service.EventService
import com.meventus.domain.service.ParticipantService
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object WebAppServer {

    private val moscowZone = ZoneId.of("Europe/Moscow")
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
            if (exchange.requestMethod == "OPTIONS") { sendCors(exchange); return@createContext }
            val userId = authenticate(exchange)?.id ?: 0L
            val stats = StatsStorage.get(userId)
            val topWordJson = stats.topWord?.jsonStr() ?: "null"
            sendJson(exchange, """{"messageCount":${stats.messageCount},"topWord":$topWordJson}""")
        }

        // GET /api/events?tags=N&search=text
        server.createContext("/api/events") { exchange ->
            if (exchange.requestMethod == "OPTIONS") { sendCors(exchange); return@createContext }
            val params = parseQuery(exchange.requestURI.query)
            val userId = authenticate(exchange)?.id ?: 0L
            val tagBitmask = params["tags"]?.toIntOrNull() ?: 0
            val search = params["search"]?.lowercase() ?: ""

            val tags = EventTag.entries.filter { tagBitmask and it.bit != 0 }.toSet()
            val publicEvents = if (tags.isEmpty()) eventService.listUpcoming() else eventService.listByTags(tags)
            val events = if (userId > 0 && tags.isEmpty()) {
                (publicEvents + eventService.listByOwner(userId) + participantService.listEventsByUser(userId))
                    .distinctBy { it.id }
                    .sortedBy { it.startsAt }
            } else {
                publicEvents
            }

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
                    append("""{"id":${e.id},"title":${e.title.jsonStr()},"shortDescription":${e.shortDescription.jsonStr()},"address":${e.address.jsonStr()},"startsAt":"${dtf.format(e.startsAt)}","cost":${e.cost},"tags":$tagJson,"participantCount":${participants.size},"isParticipant":$isParticipant,"ownerId":${e.ownerId},"status":"${e.status.name}","visibility":"${e.visibility.name}","registrationMode":"${e.registrationMode.name}","photoUrl":$photoUrl}""")
                }
                append("]")
            }
            sendJson(exchange, json)
        }

        // GET /api/event?id=X
        server.createContext("/api/event") { exchange ->
            if (exchange.requestMethod == "OPTIONS") { sendCors(exchange); return@createContext }
            val params = parseQuery(exchange.requestURI.query)
            val id = params["id"]?.toLongOrNull() ?: run { send404(exchange); return@createContext }
            val userId = authenticate(exchange)?.id ?: 0L
            val event = eventService.findById(id) ?: run { send404(exchange); return@createContext }
            if (event.visibility == EventVisibility.PRIVATE &&
                event.ownerId != userId &&
                !participantService.isParticipant(id, userId)
            ) {
                send404(exchange)
                return@createContext
            }
            val isParticipant = userId > 0 && participantService.isParticipant(id, userId)
            val participants = participantService.listByEvent(id)
            val totalContrib = participants.sumOf { it.contributed }
            val tagJson = event.tags.joinToString(",", "[", "]") { "\"${it.name}\"" }
            val photoUrl = event.photoFileId?.let { "\"/api/photo?fileId=${it}\"" } ?: "null"
            val json = """{"id":${event.id},"title":${event.title.jsonStr()},"shortDescription":${event.shortDescription.jsonStr()},"description":${event.description.jsonStr()},"address":${event.address.jsonStr()},"startsAt":"${dtf.format(event.startsAt)}","cost":${event.cost},"tags":$tagJson,"participantCount":${participants.size},"totalContributed":$totalContrib,"isParticipant":$isParticipant,"ownerId":${event.ownerId},"status":"${event.status.name}","visibility":"${event.visibility.name}","registrationMode":"${event.registrationMode.name}","photoUrl":$photoUrl,"paymentType":"${event.paymentType.name}","sbpPhone":${event.sbpPhone?.jsonStr() ?: "null"},"sbpName":${event.sbpName?.jsonStr() ?: "null"}}"""
            sendJson(exchange, json)
        }

        // POST /api/join  body: eventId=X
        server.createContext("/api/join") { exchange ->
            if (exchange.requestMethod == "OPTIONS") { sendCors(exchange); return@createContext }
            val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            val params = parseQuery(body)
            val eventId = params["eventId"]?.toLongOrNull() ?: run { sendErr(exchange, "bad eventId"); return@createContext }
            val userId = authenticate(exchange)?.id ?: run { sendUnauthorized(exchange); return@createContext }
            val event = eventService.findById(eventId) ?: run { send404(exchange); return@createContext }
            if (event.ownerId == userId) {
                sendJson(exchange, """{"ok":false,"error":"owner cannot join own event"}""")
                return@createContext
            }
            if (participantService.isParticipant(eventId, userId)) {
                sendJson(exchange, """{"ok":false,"error":"already joined"}""")
                return@createContext
            }
            if (event.visibility == EventVisibility.PRIVATE) {
                sendJson(exchange, """{"ok":false,"error":"private event"}""")
                return@createContext
            }
            if (event.registrationMode == EventRegistrationMode.INVITE_ONLY) {
                sendJson(exchange, """{"ok":false,"error":"invite only event"}""")
                return@createContext
            }
            if (event.paymentType == PaymentType.ADVANCE) {
                val costStr = if (event.cost > 0) "${event.cost}₽" else "бесплатно"
                val text = "💳 *Оплата заранее через СБП*\n\nМероприятие: *${event.title}*\nСумма: $costStr\nНомер: `${event.sbpPhone}`\nПолучатель: *${event.sbpName}*\n\nПосле перевода нажмите кнопку ниже — организатор получит уведомление и подтвердит оплату."
                val keyboard = """{"inline_keyboard":[[{"text":"📲 Я перевёл деньги","callback_data":"pay_sent:${event.id}"}]]}"""
                sendTelegramMessage(userId, text, keyboard)
                sendJson(exchange, """{"ok":true,"paymentRequired":true}""")
                return@createContext
            }
            participantService.join(eventId, userId)
            sendTelegramMessage(event.ownerId, "Новый участник записался на *${event.title}* через Mini App.")
            sendJson(exchange, """{"ok":true}""")
        }

        // POST /api/leave  body: eventId=X
        server.createContext("/api/leave") { exchange ->
            if (exchange.requestMethod == "OPTIONS") { sendCors(exchange); return@createContext }
            val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            val params = parseQuery(body)
            val eventId = params["eventId"]?.toLongOrNull() ?: run { sendErr(exchange, "bad eventId"); return@createContext }
            val userId = authenticate(exchange)?.id ?: run { sendUnauthorized(exchange); return@createContext }
            val event = eventService.findById(eventId)
            participantService.leave(eventId, userId)
            if (event != null && event.ownerId != userId) {
                sendTelegramMessage(event.ownerId, "Участник вышел из мероприятия *${event.title}* через Mini App.")
            }
            sendJson(exchange, """{"ok":true}""")
        }

        // GET /api/admin?userId=X — events owned by user with participant + contribution stats
        server.createContext("/api/admin") { exchange ->
            if (exchange.requestMethod == "OPTIONS") { sendCors(exchange); return@createContext }
            val userId = authenticate(exchange)?.id ?: run { sendUnauthorized(exchange); return@createContext }
            val events = eventService.listByOwner(userId)
            val json = buildString {
                append("[")
                events.forEachIndexed { i, e ->
                    if (i > 0) append(",")
                    val participants = participantService.listByEvent(e.id)
                    val totalContrib = participants.sumOf { it.contributed }
                    val tagJson = e.tags.joinToString(",", "[", "]") { "\"${it.name}\"" }
                    append("""{"id":${e.id},"title":${e.title.jsonStr()},"address":${e.address.jsonStr()},"startsAt":"${dtf.format(e.startsAt)}","cost":${e.cost},"tags":$tagJson,"participantCount":${participants.size},"totalContributed":$totalContrib,"status":"${e.status.name}","visibility":"${e.visibility.name}","registrationMode":"${e.registrationMode.name}"}""")
                }
                append("]")
            }
            sendJson(exchange, json)
        }

        // POST /api/event/create  body: title=...&shortDescription=...&description=...&address=...&startsAt=YYYY-MM-DDTHH:MM&cost=N&tags=N&visibility=PUBLIC
        server.createContext("/api/event/create") { exchange ->
            if (exchange.requestMethod == "OPTIONS") { sendCors(exchange); return@createContext }
            val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            val params = parseQuery(body)
            val userId = authenticate(exchange)?.id ?: run { sendUnauthorized(exchange); return@createContext }
            val title = params["title"]?.takeIf { it.isNotBlank() } ?: run { sendErr(exchange, "bad title"); return@createContext }
            val shortDescription = params["shortDescription"] ?: ""
            val description = params["description"] ?: ""
            val address = params["address"] ?: ""
            val startsAt = parseMiniAppInstant(params["startsAt"]) ?: run { sendErr(exchange, "bad startsAt format"); return@createContext }
            val cost = params["cost"]?.toLongOrNull() ?: 0L
            val tagBitmask = params["tags"]?.toIntOrNull() ?: 0
            val tags = EventTag.entries.filter { tagBitmask and it.bit != 0 }.toSet()
            val visibility = parseVisibility(params["visibility"])
            val registrationMode = parseRegistrationMode(params["registrationMode"])
            val paymentType = parsePaymentType(params["paymentType"])
            val sbpPhone = params["sbpPhone"]?.takeIf { it.isNotBlank() }
            val sbpName = params["sbpName"]?.takeIf { it.isNotBlank() }

            val event = eventService.create(
                ownerId = userId,
                title = title,
                shortDescription = shortDescription,
                description = description,
                address = address,
                startsAt = startsAt,
                cost = cost,
                photoFileId = null,
                tags = tags,
                paymentType = paymentType,
                sbpPhone = if (paymentType == PaymentType.ADVANCE) sbpPhone else null,
                sbpName = if (paymentType == PaymentType.ADVANCE) sbpName else null,
                visibility = visibility,
                registrationMode = registrationMode,
            )
            sendJson(exchange, """{"ok":true,"id":${event.id}}""")
        }

        // POST /api/event/update  body: id=X&title=...&shortDescription=...&description=...&address=...&startsAt=YYYY-MM-DDTHH:MM&cost=N&tags=N
        server.createContext("/api/event/update") { exchange ->
            if (exchange.requestMethod == "OPTIONS") { sendCors(exchange); return@createContext }
            val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            val params = parseQuery(body)
            val id = params["id"]?.toLongOrNull() ?: run { sendErr(exchange, "bad id"); return@createContext }
            val userId = authenticate(exchange)?.id ?: run { sendUnauthorized(exchange); return@createContext }
            val title = params["title"]?.takeIf { it.isNotBlank() } ?: run { sendErr(exchange, "bad title"); return@createContext }
            val shortDescription = params["shortDescription"] ?: ""
            val description = params["description"] ?: ""
            val address = params["address"] ?: ""
            val startsAtStr = params["startsAt"] ?: run { sendErr(exchange, "bad startsAt"); return@createContext }
            val cost = params["cost"]?.toLongOrNull() ?: 0L
            val tagBitmask = params["tags"]?.toIntOrNull() ?: 0
            val tags = EventTag.entries.filter { tagBitmask and it.bit != 0 }.toSet()
            val visibility = parseVisibility(params["visibility"])
            val registrationMode = parseRegistrationMode(params["registrationMode"])
            val paymentType = parsePaymentType(params["paymentType"])
            val sbpPhone = params["sbpPhone"]?.takeIf { it.isNotBlank() }
            val sbpName = params["sbpName"]?.takeIf { it.isNotBlank() }

            val startsAt = parseMiniAppInstant(startsAtStr) ?: run { sendErr(exchange, "bad startsAt format"); return@createContext }

            val updated = eventService.update(
                id,
                userId,
                title,
                shortDescription,
                description,
                address,
                startsAt,
                cost,
                tags,
                paymentType = paymentType,
                sbpPhone = sbpPhone,
                sbpName = sbpName,
                visibility = visibility,
                registrationMode = registrationMode,
            )
            if (updated == null) {
                sendJson(exchange, """{"ok":false,"error":"not found or not owner"}""")
            } else {
                sendJson(exchange, """{"ok":true,"id":${updated.id}}""")
            }
        }

        // POST /api/broadcast body: eventId=X&message=text
        server.createContext("/api/broadcast") { exchange ->
            if (exchange.requestMethod == "OPTIONS") { sendCors(exchange); return@createContext }
            val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            val params = parseQuery(body)
            val userId = authenticate(exchange)?.id ?: run { sendUnauthorized(exchange); return@createContext }
            val eventId = params["eventId"]?.toLongOrNull() ?: run { sendErr(exchange, "bad eventId"); return@createContext }
            val message = params["message"]?.takeIf { it.isNotBlank() } ?: run { sendErr(exchange, "bad message"); return@createContext }
            val event = eventService.findById(eventId) ?: run { send404(exchange); return@createContext }
            if (event.ownerId != userId) {
                sendUnauthorized(exchange)
                return@createContext
            }

            val participants = participantService.listByEvent(eventId)
            val text = "📢 *${event.title}*\n\n$message"
            participants.forEach { participant ->
                sendTelegramMessage(participant.userId, text)
            }
            sendJson(exchange, """{"ok":true,"sent":${participants.size},"total":${participants.size}}""")
        }

        // POST /api/event/delete body: id=X
        server.createContext("/api/event/delete") { exchange ->
            if (exchange.requestMethod == "OPTIONS") { sendCors(exchange); return@createContext }
            val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            val params = parseQuery(body)
            val userId = authenticate(exchange)?.id ?: run { sendUnauthorized(exchange); return@createContext }
            val id = params["id"]?.toLongOrNull() ?: run { sendErr(exchange, "bad id"); return@createContext }
            val event = eventService.findById(id) ?: run { send404(exchange); return@createContext }
            if (event.ownerId != userId) {
                sendUnauthorized(exchange)
                return@createContext
            }
            val deleted = eventService.delete(id, userId)
            if (deleted) {
                event.groupChatId?.let { groupChatId ->
                    sendTelegramMessage(groupChatId, "Мероприятие *${event.title}* удалено.")
                }
                sendJson(exchange, """{"ok":true}""")
            } else {
                sendJson(exchange, """{"ok":false,"error":"not found or not owner"}""")
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

    private fun sendUnauthorized(exchange: HttpExchange) {
        val bytes = """{"ok":false,"error":"unauthorized"}""".toByteArray()
        exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
        exchange.sendResponseHeaders(401, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun send404(exchange: HttpExchange) {
        val bytes = "Not found".toByteArray()
        exchange.sendResponseHeaders(404, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun sendCors(exchange: HttpExchange) {
        exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
        exchange.responseHeaders.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        exchange.responseHeaders.set("Access-Control-Allow-Headers", "Content-Type, X-Telegram-Init-Data")
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

    private data class AuthUser(val id: Long)

    private fun authenticate(exchange: HttpExchange): AuthUser? {
        val initData = exchange.requestHeaders.getFirst("X-Telegram-Init-Data")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val params = parseQuery(initData)
        val receivedHash = params["hash"] ?: return null

        val dataCheckString = params
            .filterKeys { it != "hash" }
            .toSortedMap()
            .entries
            .joinToString("\n") { (key, value) -> "$key=$value" }

        val secretKey = hmacSha256("WebAppData".toByteArray(Charsets.UTF_8), botToken)
        val calculatedHash = hmacSha256(secretKey, dataCheckString).toHex()
        if (!constantTimeEquals(calculatedHash, receivedHash)) return null

        val userJson = params["user"] ?: return null
        val userId = """"id"\s*:\s*(\d+)""".toRegex()
            .find(userJson)
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()
            ?: return null

        return AuthUser(userId)
    }

    private fun hmacSha256(key: ByteArray, data: String): ByteArray =
        hmacSha256(key, data.toByteArray(Charsets.UTF_8))

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun constantTimeEquals(left: String, right: String): Boolean {
        if (left.length != right.length) return false
        var result = 0
        for (i in left.indices) {
            result = result or (left[i].code xor right[i].code)
        }
        return result == 0
    }

    private fun parseQuery(query: String?): Map<String, String> =
        query?.split("&")
            ?.mapNotNull {
                val idx = it.indexOf('=')
                if (idx < 0) null else it.substring(0, idx) to java.net.URLDecoder.decode(it.substring(idx + 1), "UTF-8")
            }
            ?.toMap() ?: emptyMap()

    private fun parseMiniAppInstant(value: String?): Instant? {
        val raw = value?.take(16) ?: return null
        return runCatching {
            // datetime-local has no zone; Mini App displays and edits dates in Moscow time.
            val ldt = LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
            ldt.atZone(moscowZone).toInstant()
        }.getOrNull()
    }

    private fun parseVisibility(value: String?): EventVisibility =
        runCatching { EventVisibility.valueOf(value?.uppercase() ?: EventVisibility.PUBLIC.name) }
            .getOrDefault(EventVisibility.PUBLIC)

    private fun parsePaymentType(value: String?): PaymentType =
        runCatching { PaymentType.valueOf(value?.uppercase() ?: PaymentType.ON_SITE.name) }
            .getOrDefault(PaymentType.ON_SITE)

    private fun parseRegistrationMode(value: String?): EventRegistrationMode =
        runCatching { EventRegistrationMode.valueOf(value?.uppercase() ?: EventRegistrationMode.FREE.name) }
            .getOrDefault(EventRegistrationMode.FREE)

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
