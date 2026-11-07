package com.focusrefine.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxGroupsTest {

    private fun muted(id: Int, pkg: String, confidence: Float? = 0.9f) = NotificationEntity(
        id = id, title = "t$id", text = "x", packageName = pkg, timestamp = 1000L - id,
        isUrgent = false, confidence = confidence
    )

    @Test
    fun groupingOffGivesOneRowPerNotification() {
        val list = listOf(muted(1, "com.a"), muted(2, "com.a"))
        val rows = inboxRows(list, groupByApp = false)
        assertEquals(2, rows.size)
        assertTrue(rows.all { it is InboxRow.Single })
    }

    @Test
    fun appsWithTwoOrMoreItemsBecomeOneGroupAtTheNewestPosition() {
        // Newest first: a, b, a, c, a
        val list = listOf(muted(1, "com.a"), muted(2, "com.b"), muted(3, "com.a"), muted(4, "com.c"), muted(5, "com.a"))
        val rows = inboxRows(list, groupByApp = true)

        assertEquals(3, rows.size)
        val group = (rows[0] as InboxRow.Group).group
        assertEquals("com.a", group.packageName)
        assertEquals(listOf(1, 3, 5), group.items.map { it.id })
        // b and c each have one item, so they stay plain cards
        assertEquals(2, (rows[1] as InboxRow.Single).notif.id)
        assertEquals(4, (rows[2] as InboxRow.Single).notif.id)
    }

    @Test
    fun everyNotificationAppearsExactlyOnce() {
        val list = (1..30).map { muted(it, "com.app${it % 4}") }
        val rows = inboxRows(list, groupByApp = true)
        val ids = rows.flatMap { row ->
            when (row) {
                is InboxRow.Single -> listOf(row.notif.id)
                is InboxRow.Group -> row.group.items.map { it.id }
            }
        }
        assertEquals(list.map { it.id }.sorted(), ids.sorted())
    }

    @Test
    fun summaryNamesTheAppAndCountsCloseCalls() {
        val group = MutedAppGroup(
            "com.amazon.mShop.android.shopping",
            listOf(muted(1, "com.amazon.mShop.android.shopping"), muted(2, "com.amazon.mShop.android.shopping", confidence = 0.6f))
        )
        assertEquals("2 from Amazon · 1 close call", group.summary("Amazon"))
    }

    @Test
    fun summaryWithoutCloseCallsIsShort() {
        val group = MutedAppGroup("com.spotify.music", listOf(muted(1, "com.spotify.music"), muted(2, "com.spotify.music")))
        assertEquals("2 from Spotify", group.summary("Spotify"))
    }
}
