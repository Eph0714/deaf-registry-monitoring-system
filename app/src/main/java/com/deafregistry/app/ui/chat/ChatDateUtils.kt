package com.deafregistry.app.ui.chat

import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Backend sends raw Postgres TIMESTAMP strings ("YYYY-MM-DD HH:MM:SS[.ffffff]") - parsed here as
 * a plain LocalDateTime with no zone conversion, matching how the rest of the app already treats
 * these timestamps (e.g. VisitDueWorker's overdue-day calculation). */
fun parseServerDateTime(raw: String?): LocalDateTime? {
    if (raw.isNullOrBlank()) return null
    return runCatching { LocalDateTime.parse(raw.take(19).replace(' ', 'T')) }.getOrNull()
}

fun formatServerDateTime(raw: String?, pattern: String = "MMM d, yyyy h:mm a"): String {
    val parsed = parseServerDateTime(raw) ?: return raw ?: "-"
    return parsed.format(DateTimeFormatter.ofPattern(pattern))
}

fun formatClockTime(raw: String?): String = formatServerDateTime(raw, "h:mm a")

fun formatCountdown(remaining: Duration): String {
    if (remaining.isNegative || remaining.isZero) return "00:00:00"
    val totalSeconds = remaining.seconds
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}
