package com.focusrefine.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone

class NotificationTimeTest {

    private val zone = ZoneId.of("UTC")
    private val locale = Locale.US
    private val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT, locale).apply { timeZone = TimeZone.getTimeZone("UTC") }

    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun timeOf(iso: String) = timeFormat.format(java.util.Date(at(iso)))

    @Test
    fun `today shows the time alone`() {
        val now = at("2026-11-01T08:00:00Z")
        val shown = formatNotificationTime(at("2026-11-01T07:30:00Z"), timeFormat, now, zone, locale)
        assertEquals(timeOf("2026-11-01T07:30:00Z"), shown)
    }

    @Test
    fun `yesterday adds the date with its year`() {
        val now = at("2026-11-01T08:00:00Z")
        val shown = formatNotificationTime(at("2026-10-31T10:42:00Z"), timeFormat, now, zone, locale)
        assertEquals("Oct 31, 2026, " + timeOf("2026-10-31T10:42:00Z"), shown)
    }

    @Test
    fun `an earlier year keeps the year, so the same clock time is not mistaken for today`() {
        val now = at("2026-11-01T08:00:00Z")
        val shown = formatNotificationTime(at("2025-11-01T08:00:00Z"), timeFormat, now, zone, locale)
        assertEquals("Nov 1, 2025, " + timeOf("2025-11-01T08:00:00Z"), shown)
    }

    @Test
    fun `the day is taken in the device's time zone`() {
        // 23:30 UTC on Oct 31 is already Nov 1 in UTC+1: that counts as today there.
        val plusOne = ZoneId.of("Europe/Paris")
        val now = at("2026-11-01T08:00:00Z")
        val shown = formatNotificationTime(at("2026-10-31T23:30:00Z"), timeFormat, now, plusOne, locale)
        assertEquals(timeOf("2026-10-31T23:30:00Z"), shown)
    }
}
