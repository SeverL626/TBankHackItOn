package com.meventus.util.extensions

fun String.truncate(max: Int): String =
    if (length <= max) this else take(max - 1) + "…"

fun String?.orDash(): String = this?.takeIf { it.isNotBlank() } ?: "—"
