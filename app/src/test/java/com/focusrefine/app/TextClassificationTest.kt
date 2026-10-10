package com.focusrefine.app

import org.junit.Assert.assertEquals
import org.junit.Test

class TextClassificationTest {

    private fun classifyNotification(title: String, text: String, modePrompt: String): String {
        val content = "$title $text".lowercase()
        val keywords = modePrompt.lowercase().split(",", " ").map { it.trim() }.filter { it.isNotEmpty() }
        val isUrgent = if (keywords.isEmpty()) content.contains("urgent") else keywords.any { content.contains(it) }
        return if (isUrgent) "SHOW" else "HIDE"
    }

    @Test
    fun testAiGeneratedTestCasesCommuteMode() {
        val prompt = "navigation, turn left, turn right, meeting, traffic, call"

        // AI Generated Urgent Cases (Should be classified as SHOW)
        assertEquals("SHOW", classifyNotification("Google Maps", "Turn left on Main Street in 500 feet", prompt))
        assertEquals("SHOW", classifyNotification("Phone", "Incoming call from Office", prompt))
        assertEquals("SHOW", classifyNotification("Calendar", "Your team meeting starts in 10 minutes", prompt))

        // AI Generated Non-Urgent Cases (Should be classified as HIDE)
        assertEquals("HIDE", classifyNotification("Instagram", "Liked your photo", prompt))
        assertEquals("HIDE", classifyNotification("YouTube", "New video uploaded by your favorite creator", prompt))
        assertEquals("HIDE", classifyNotification("Shopping App", "Flash sale: 70% off everything today!", prompt))
    }

    @Test
    fun testAiGeneratedTestCasesFocusMode() {
        val prompt = "call, boss, emergency"

        // AI Generated Urgent Cases (SHOW)
        assertEquals("SHOW", classifyNotification("Phone", "Missed call from Boss", prompt))
        assertEquals("SHOW", classifyNotification("Alert", "Emergency security notification", prompt))

        // AI Generated Non-Urgent Cases (HIDE)
        assertEquals("HIDE", classifyNotification("Slack", "New message in #watercooler", prompt))
        assertEquals("HIDE", classifyNotification("News", "Breaking news update: stock market closes higher", prompt))
    }

    @Test
    fun testAiGeneratedTestCasesReadingMode() {
        val prompt = "emergency, hospital"

        // AI Generated Urgent Cases (SHOW)
        assertEquals("SHOW", classifyNotification("Hospital", "Your lab test results are ready", prompt))

        // AI Generated Non-Urgent Cases (HIDE)
        assertEquals("HIDE", classifyNotification("Email", "Weekly newsletter subscription", prompt))
        assertEquals("HIDE", classifyNotification("WhatsApp", "Group chat message from friends", prompt))
    }
}
