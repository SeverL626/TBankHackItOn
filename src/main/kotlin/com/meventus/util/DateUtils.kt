package com.meventus.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object DateUtils {
    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    private val zone: ZoneId = ZoneId.of("Europe/Moscow")

    fun parse(input: String): Instant =
        LocalDateTime.parse(input, formatter).atZone(zone).toInstant()

    fun format(instant: Instant): String =
        formatter.format(instant.atZone(zone))
}
