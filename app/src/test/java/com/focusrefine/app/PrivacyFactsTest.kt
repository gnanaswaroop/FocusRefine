package com.focusrefine.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyFactsTest {

    @Test
    fun anAppWithoutNetworkPermissionsWorksOffline() {
        val requested = arrayOf(
            "android.permission.POST_NOTIFICATIONS", "android.permission.FOREGROUND_SERVICE",
            "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
        )
        assertEquals(emptyList<String>(), PrivacyFacts.networkPermissionsIn(requested))
    }

    @Test
    fun everyNetworkRelatedPermissionIsReported() {
        val requested = arrayOf(
            "android.permission.INTERNET", "android.permission.POST_NOTIFICATIONS", "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE"
        )
        assertEquals(
            listOf("android.permission.ACCESS_NETWORK_STATE", "android.permission.ACCESS_WIFI_STATE", "android.permission.INTERNET"),
            PrivacyFacts.networkPermissionsIn(requested)
        )
    }

    @Test
    fun noPermissionListMeansNoNetworkPermissions() {
        assertEquals(emptyList<String>(), PrivacyFacts.networkPermissionsIn(null))
    }

    @Test
    fun theOfflineVerdictFollowsThePermissions() {
        fun facts(perms: List<String>) = PrivacyFacts(perms, false, true, true, 0, 0, 0)
        assertTrue(facts(emptyList()).worksOffline)
        assertFalse(facts(listOf("android.permission.INTERNET")).worksOffline)
    }
}
