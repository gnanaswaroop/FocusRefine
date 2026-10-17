package com.focusrefine.app

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class NotificationFilterE2ETest {

    private lateinit var db: AppDatabase
    private lateinit var dao: NotificationDao
    private lateinit var context: Context

    @Before
    fun createDb() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.notificationDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun testNotificationBarToggleStartStopSync() = runBlocking {
        val prefs = context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)

        // 1. Start with service_running = true
        prefs.edit().putBoolean("service_running", true).putInt("suppressed_count", 5).apply()
        assertEquals(true, prefs.getBoolean("service_running", false))

        // 2. Simulate notification bar STOP toggle
        val toggleReceiver = NotificationToggleReceiver()
        val toggleIntent = Intent(NotificationFilterService.ACTION_TOGGLE_SERVICE).apply {
            setPackage(context.packageName)
        }
        toggleReceiver.onReceive(context, toggleIntent)

        // 3. Verify application state toggled to STOP (false) and suppressed count reset
        assertEquals(false, prefs.getBoolean("service_running", true))
        assertEquals(0, prefs.getInt("suppressed_count", -1))

        // 4. Simulate notification bar START toggle
        toggleReceiver.onReceive(context, toggleIntent)

        // 5. Verify application state toggled back to START (true)
        assertEquals(true, prefs.getBoolean("service_running", false))
    }

    @Test
    fun testDummyNotificationPersistenceFlow() = runBlocking {
        val urgentEntity = NotificationEntity(
            title = "Google Maps",
            text = "Turn right in 200 feet",
            packageName = "com.google.android.apps.maps",
            timestamp = System.currentTimeMillis(),
            isUrgent = true
        )
        dao.insert(urgentEntity)

        val mutedEntity = NotificationEntity(
            title = "WhatsApp",
            text = "Hey! What's up?",
            packageName = "com.whatsapp",
            timestamp = System.currentTimeMillis() + 1000,
            isUrgent = false
        )
        dao.insert(mutedEntity)

        val shownList = dao.getByUrgency(true)
        assertEquals(1, shownList.size)
        assertEquals("Google Maps", shownList[0].title)
        assertEquals(true, shownList[0].isUrgent)

        val mutedList = dao.getByUrgency(false)
        assertEquals(1, mutedList.size)
        assertEquals("WhatsApp", mutedList[0].title)
        assertEquals(false, mutedList[0].isUrgent)
    }

    @Test
    fun testBackgroundServiceFilteringLogic() = runBlocking {
        // Simulate background service evaluating incoming notifications against Commute prompt rules while app is minimized
        val prompt = "navigation, turn left, turn right, meeting, traffic, call"
        val keywords = prompt.lowercase().split(",", " ").map { it.trim() }.filter { it.isNotEmpty() }

        val notificationTitle = "Slack"
        val notificationText = "Hey, are we still having that meeting?"
        val content = "$notificationTitle $notificationText".lowercase()

        val isUrgent = keywords.any { content.contains(it) }
        assertEquals(true, isUrgent) // "meeting" matches commute prompt

        val entity = NotificationEntity(
            title = notificationTitle,
            text = notificationText,
            packageName = "com.Slack",
            timestamp = System.currentTimeMillis(),
            isUrgent = isUrgent
        )
        dao.insert(entity)

        val shownList = dao.getByUrgency(true)
        assertEquals(1, shownList.size)
        assertEquals("Slack", shownList[0].title)
    }

    @Test
    fun testTextClassificationAndFilteringFlow() = runBlocking {
        // Test text classification and filtering flow (testing both urgent and muted database classification)
        val urgentNotification = NotificationEntity(
            title = "Emergency Alert",
            text = "Severe weather warning in your area",
            packageName = "com.weather.alert",
            timestamp = System.currentTimeMillis(),
            isUrgent = true
        )
        dao.insert(urgentNotification)

        val mutedNotification = NotificationEntity(
            title = "Game Update",
            text = "Your daily bonus is ready to claim!",
            packageName = "com.game.app",
            timestamp = System.currentTimeMillis() + 500,
            isUrgent = false
        )
        dao.insert(mutedNotification)

        val shown = dao.getByUrgency(true)
        val muted = dao.getByUrgency(false)

        assertEquals(1, shown.size)
        assertEquals("Emergency Alert", shown[0].title)

        assertEquals(1, muted.size)
        assertEquals("Game Update", muted[0].title)
    }

    @Test
    fun testNotificationSearchByUrgency() = runBlocking {
        dao.insert(NotificationEntity(title = "Google Maps", text = "Turn right on Oak Street", packageName = "com.maps", timestamp = System.currentTimeMillis(), isUrgent = true))
        dao.insert(NotificationEntity(title = "Uber", text = "Driver arriving soon", packageName = "com.uber", timestamp = System.currentTimeMillis() - 100, isUrgent = true))
        dao.insert(NotificationEntity(title = "WhatsApp", text = "Coffee later?", packageName = "com.whatsapp", timestamp = System.currentTimeMillis() - 200, isUrgent = false))

        val searchShown = dao.searchByUrgency(true, "%Maps%")
        assertEquals(1, searchShown.size)
        assertEquals("Google Maps", searchShown[0].title)

        val searchMuted = dao.searchByUrgency(false, "%Coffee%")
        assertEquals(1, searchMuted.size)
        assertEquals("WhatsApp", searchMuted[0].title)
    }

    @Test
    fun testLearnedRuleMutedToShownOverride() = runBlocking {
        // 1. Initially muted notification
        val initialMuted = NotificationEntity(
            title = "WhatsApp",
            text = "Coffee later?",
            packageName = "com.whatsapp",
            timestamp = System.currentTimeMillis(),
            isUrgent = false,
            isLearned = false
        )
        dao.insert(initialMuted)

        // 2. User toggles/swaps to Shown (isUrgent = true, isLearned = true)
        val toggledShown = initialMuted.copy(isUrgent = true, isLearned = true)
        dao.insert(toggledShown)

        // 3. Verify learned rule lookup overrides future classification to SHOWN
        val matchedRule = dao.findMatchingLearnedRule("WhatsApp", "%Coffee%")
        assertNotNull(matchedRule)
        assertEquals(true, matchedRule?.isUrgent)
        assertEquals(true, matchedRule?.isLearned)
    }

    @Test
    fun testLearnedRuleShownToMutedOverride() = runBlocking {
        // 1. Initially shown notification
        val initialShown = NotificationEntity(
            title = "Google Maps",
            text = "Turn right on Oak Street",
            packageName = "com.maps",
            timestamp = System.currentTimeMillis(),
            isUrgent = true,
            isLearned = false
        )
        dao.insert(initialShown)

        // 2. User toggles/swaps to Muted (isUrgent = false, isLearned = true)
        val toggledMuted = initialShown.copy(isUrgent = false, isLearned = true)
        dao.insert(toggledMuted)

        // 3. Verify learned rule lookup overrides future classification to MUTED
        val matchedRule = dao.findMatchingLearnedRule("Google Maps", "%Oak Street%")
        assertNotNull(matchedRule)
        assertEquals(false, matchedRule?.isUrgent)
        assertEquals(true, matchedRule?.isLearned)
    }

    @Test
    fun testFilteringStrengthAndExceptionsPreferences() = runBlocking {
        val prefs = context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("filter_strength", 2)
            .putBoolean("exception_nav_prompts", true)
            .putBoolean("exception_calls", false)
            .apply()

        assertEquals(2, prefs.getInt("filter_strength", 0))
        assertEquals(true, prefs.getBoolean("exception_nav_prompts", false))
        assertEquals(false, prefs.getBoolean("exception_calls", true))
    }

    @Test
    fun testManualModeSwitchAndAppRulePreferences() = runBlocking {
        val prefs = context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("manual_mode_switch", true)
            .putInt("app_rule_type_whatsapp", 0)
            .putBoolean("app_mute_casual_whatsapp", true)
            .apply()

        assertEquals(true, prefs.getBoolean("manual_mode_switch", false))
        assertEquals(0, prefs.getInt("app_rule_type_whatsapp", -1))
        assertEquals(true, prefs.getBoolean("app_mute_casual_whatsapp", false))
    }

    @Test
    fun testNoiseFilteredPercentageResetOnClear() = runBlocking {
        val prefs = context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)
        dao.insert(NotificationEntity(title = "Maps", text = "Turn left", packageName = "com.maps", timestamp = System.currentTimeMillis(), isUrgent = true))
        dao.insert(NotificationEntity(title = "WhatsApp", text = "Coffee?", packageName = "com.whatsapp", timestamp = System.currentTimeMillis(), isUrgent = false))

        var muted = dao.getByUrgency(false).size
        var urgent = dao.getByUrgency(true).size
        var total = muted + urgent
        var percent = if (total > 0) (muted * 100 / total) else 0

        assertEquals(1, muted)
        assertEquals(1, urgent)
        assertEquals(50, percent)

        // Clear inbox data
        dao.deleteAll()
        prefs.edit().putInt("suppressed_count", 0).apply()

        muted = dao.getByUrgency(false).size
        urgent = dao.getByUrgency(true).size
        total = muted + urgent
        percent = if (total > 0) (muted * 100 / total) else 0

        assertEquals(0, muted)
        assertEquals(0, urgent)
        assertEquals(0, percent)
    }

    @Test
    fun testAiRuleSynthesizerAndSemanticMatching() = runBlocking {
        val synthesizedRule = AiRuleSynthesizer.synthesizeRule(
            packageName = "com.whatsapp",
            title = "WhatsApp",
            text = "Want to grab coffee later?",
            isUrgent = false,
            activeMode = "Deep Work / Focus"
        )

        assertEquals(true, synthesizedRule.contains("[MUTE]"))
        assertEquals(true, synthesizedRule.lowercase().contains("coffee") || synthesizedRule.lowercase().contains("grab"))

        // Test token matching on similar notification text ("coffee tomorrow")
        val matchesCoffee = AiRuleSynthesizer.matchesRule(synthesizedRule, "WhatsApp", "Free for coffee tomorrow?")
        assertEquals(true, matchesCoffee)

        // Insert entity with synthesized rule into Room DB and query getSynthesizedRules()
        val entity = NotificationEntity(
            title = "WhatsApp",
            text = "Want to grab coffee later?",
            packageName = "com.whatsapp",
            timestamp = System.currentTimeMillis(),
            isUrgent = false,
            isLearned = true,
            synthesizedRule = synthesizedRule
        )
        dao.insert(entity)

        val activeRules = dao.getSynthesizedRules()
        assertEquals(1, activeRules.size)
        assertEquals(synthesizedRule, activeRules[0].synthesizedRule)
    }

    @Test
    fun testAiLearningFlow_MutedToShown_OppositeClassification() = runBlocking {
        // 1. Initial Notification originally classified as MUTED
        val pkg = "com.devops.monitoring"
        val initialNotif = NotificationEntity(
            title = "Pipeline Alert",
            text = "Build #101 deploy finished",
            packageName = pkg,
            timestamp = System.currentTimeMillis(),
            isUrgent = false,
            isLearned = false
        )
        dao.insert(initialNotif)
        assertEquals(false, dao.getAll().first { it.title == "Pipeline Alert" }.isUrgent)

        // 2. User reclassifies to SHOWN: AI rule synthesized
        val aiRule = AiRuleSynthesizer.synthesizeRule(pkg, "Pipeline Alert", "Build #101 deploy finished", true, "Deep Focus")
        val reclassifiedNotif = initialNotif.copy(isUrgent = true, isLearned = true, synthesizedRule = aiRule)
        dao.insert(reclassifiedNotif)

        // 3. New incoming similar notification arrives
        val newTitle = "Pipeline Alert"
        val newText = "Build #102 deploy finished"

        // 4. Verify AI Synthesized rule matches and opposite classification (SHOWN) is applied
        val activeRules = dao.getSynthesizedRules()
        val matchingRule = activeRules.find { AiRuleSynthesizer.matchesRule(it.synthesizedRule ?: "", newTitle, newText) }

        assertNotNull(matchingRule)
        assertEquals(true, matchingRule?.isUrgent)
        assertEquals(true, aiRule.contains("[ALLOW]"))
    }

    @Test
    fun testAiLearningFlow_ShownToMuted_OppositeClassification() = runBlocking {
        // 1. Initial Notification originally classified as SHOWN
        val pkg = "com.service.notifier"
        val initialNotif = NotificationEntity(
            title = "System Alert",
            text = "Routine backup sync finished",
            packageName = pkg,
            timestamp = System.currentTimeMillis(),
            isUrgent = true,
            isLearned = false
        )
        dao.insert(initialNotif)
        assertEquals(true, dao.getAll().first { it.title == "System Alert" }.isUrgent)

        // 2. User reclassifies to MUTED: AI rule synthesized
        val aiRule = AiRuleSynthesizer.synthesizeRule(pkg, "System Alert", "Routine backup sync finished", false, "Deep Focus")
        val reclassifiedNotif = initialNotif.copy(isUrgent = false, isLearned = true, synthesizedRule = aiRule)
        dao.insert(reclassifiedNotif)

        // 3. New incoming similar notification arrives
        val newTitle = "System Alert"
        val newText = "Routine backup sync #2 finished"

        // 4. Verify AI Synthesized rule matches and opposite classification (MUTED) is applied
        val activeRules = dao.getSynthesizedRules()
        val matchingRule = activeRules.find { AiRuleSynthesizer.matchesRule(it.synthesizedRule ?: "", newTitle, newText) }

        assertNotNull(matchingRule)
        assertEquals(false, matchingRule?.isUrgent)
        assertEquals(true, aiRule.contains("[MUTE]"))
    }
}
