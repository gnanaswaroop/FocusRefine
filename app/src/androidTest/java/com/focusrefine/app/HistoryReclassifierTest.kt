package com.focusrefine.app

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Runs against the app's real database; run on an emulator or a throwaway profile only. */
class HistoryReclassifierTest {

    private lateinit var context: Context
    private lateinit var dao: NotificationDao

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        dao = AppDatabase.getDatabase(context).notificationDao()
        dao.deleteAll()
        context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE).edit()
            .putBoolean("use_ai_model", false)
            .putInt("suppressed_count", 5)
            .apply()
    }

    @After
    fun tearDown() {
        dao.deleteAll()
    }

    private fun row(title: String, text: String, urgent: Boolean, learned: Boolean = false, rule: String? = null) =
        NotificationEntity(
            title = title, text = text, packageName = "com.example.test",
            timestamp = System.currentTimeMillis(), isUrgent = urgent, isLearned = learned, synthesizedRule = rule
        )

    @Test
    fun reclassifiesHistoryAndLeavesLearnedRulesAlone() {
        // User rules: always show "PagerX", always mute "PromoY"
        dao.insert(row("PagerX", "rule row", urgent = true, learned = true, rule = "[ALLOW] Example: pagerx"))
        dao.insert(row("PromoY", "rule row", urgent = false, learned = true, rule = "[MUTE] Example: promoy"))
        // History that disagrees with those rules
        dao.insert(row("PagerX", "db failover in progress", urgent = false))   // should become Shown
        dao.insert(row("PromoY", "20% off everything", urgent = true))         // should become Muted
        // History the rules don't touch
        dao.insert(row("Qux", "nothing relevant zzz", urgent = false))

        val summary = HistoryReclassifier.run(context)

        assertEquals(3, summary.total)          // learned rule rows are not history
        assertEquals(1, summary.toShown)
        assertEquals(1, summary.toMuted)
        assertEquals(0, summary.failed)

        val all = dao.getAll()
        val pager = all.first { it.title == "PagerX" && !it.isLearned }
        val promo = all.first { it.title == "PromoY" && !it.isLearned }
        assertTrue(pager.isUrgent)
        assertFalse(promo.isUrgent)
        assertTrue(pager.explanation!!.contains("learned preference rule"))

        // learned rule rows untouched
        assertTrue(all.first { it.title == "PagerX" && it.isLearned }.isUrgent)
        assertFalse(all.first { it.title == "PromoY" && it.isLearned }.isUrgent)
        assertEquals(3, all.count { !it.isLearned })

        // one flipped each way, so the muted counter stays put
        val count = context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE).getInt("suppressed_count", -1)
        assertEquals(5, count)

        // idempotent
        val again = HistoryReclassifier.run(context)
        assertEquals(0, again.changed)
    }

    @Test
    fun emptyHistoryIsANoOp() {
        val summary = HistoryReclassifier.run(context)
        assertEquals(0, summary.total)
        assertEquals(0, summary.changed)
    }
}
