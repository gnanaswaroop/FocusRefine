package com.focusflow.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class PromptManagerTest {

    @Test
    fun testPromptConfigParsing() {
        val jsonStr = "{\"Commute\": {\"prompt\": \"navigation, traffic\", \"enabled\": true}, \"DND\": {\"prompt\": \"emergency\", \"enabled\": false}}"
        val json = JSONObject(jsonStr)
        
        val commuteObj = json.getJSONObject("Commute")
        assertEquals("navigation, traffic", commuteObj.getString("prompt"))
        assertTrue(commuteObj.getBoolean("enabled"))

        val dndObj = json.getJSONObject("DND")
        assertEquals("emergency", dndObj.getString("prompt"))
        assertEquals(false, dndObj.getBoolean("enabled"))
    }
}
