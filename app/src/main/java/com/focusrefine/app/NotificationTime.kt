package com.focusrefine.app

import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale

/**
 * How a notification's arrival time is shown. Today's notifications show the time alone, to keep the inbox compact.
 * Older ones add the date with its year, so "10:42 AM" from last year is not mistaken for this morning.
 * [timeFormat] comes from the device (12 or 24 hour setting), and the date follows the locale.
 */
fun formatNotificationTime(
    timestamp: Long,
    timeFormat: DateFormat,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault()
): String {
    val time = timeFormat.format(Date(timestamp))
    val arrived = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    if (arrived == today) return time
    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(arrived)
    return "$date, $time"
}
