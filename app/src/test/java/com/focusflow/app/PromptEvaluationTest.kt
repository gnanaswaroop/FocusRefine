package com.focusflow.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptEvaluationTest {

    private fun evaluateContent(title: String?, text: String?, prompt: String): Boolean {
        val content = "${title ?: ""} ${text ?: ""}".lowercase()
        val keywords = prompt.lowercase().split(",", " ").map { it.trim() }.filter { it.isNotEmpty() }
        if (keywords.isEmpty()) return content.contains("urgent")
        return keywords.any { keyword -> content.contains(keyword) }
    }

    @Test
    fun testCommuteModePromptMatching() {
        val prompt = "navigation, turn left, turn right, meeting, traffic, call"
        
        // Urgent / Allowed
        assertTrue(evaluateContent("Google Maps", "Turn right on Oak Street", prompt))
        assertTrue(evaluateContent("Phone", "Incoming call from Mom", prompt))
        assertTrue(evaluateContent("Calendar", "Team meeting starting in 5 minutes", prompt))

        // Suppressed / Muted
        assertFalse(evaluateContent("WhatsApp", "Hey, want to grab lunch?", prompt))
        assertFalse(evaluateContent("Promotions", "50% off your next order", prompt))
    }

    @Test
    fun testFocusModePromptMatching() {
        val prompt = "call, boss, emergency"

        // Urgent / Allowed
        assertTrue(evaluateContent("Phone", "Missed call from Boss", prompt))
        assertTrue(evaluateContent("Alert", "Emergency weather warning", prompt))

        // Suppressed / Muted
        assertFalse(evaluateContent("Slack", "New message in #general", prompt))
        assertFalse(evaluateContent("YouTube", "New video uploaded", prompt))
    }

    @Test
    fun testCustomModePromptMatching() {
        val prompt = "security, critical, server"

        // Urgent / Allowed
        assertTrue(evaluateContent("Datadog", "Critical server CPU alert", prompt))
        assertTrue(evaluateContent("AWS", "Security alert detected", prompt))

        // Suppressed / Muted
        assertFalse(evaluateContent("GitHub", "New commit pushed", prompt))
    }
}
