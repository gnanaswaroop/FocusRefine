package com.focusrefine.app

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app promises that notification content never leaves the device. These checks run against the
 * installed (merged) app, so a library that quietly adds network access or backup fails the build.
 */
class PrivacyGuardsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun appHasNoNetworkPermissions() {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions?.toList().orEmpty()
        assertFalse("INTERNET must not be requested: $requested", "android.permission.INTERNET" in requested)
        assertFalse("ACCESS_NETWORK_STATE must not be requested: $requested", "android.permission.ACCESS_NETWORK_STATE" in requested)
    }

    @Test
    fun appCannotOpenASocket() {
        // Without the INTERNET permission the OS refuses sockets with EPERM / SecurityException.
        val failed = try {
            java.net.Socket().use { it.connect(java.net.InetSocketAddress("127.0.0.1", 9), 500) }
            false
        } catch (e: java.net.ConnectException) {
            false // reached the network stack: connection refused is not a permission failure
        } catch (e: java.net.SocketException) {
            true
        } catch (e: SecurityException) {
            true
        }
        assertTrue("a socket was allowed to connect without INTERNET", failed)
    }

    @Test
    fun cloudBackupIsDisabled() {
        val flags = context.applicationInfo.flags
        assertEquals("allowBackup must be false so history and learned data are never uploaded", 0, flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }
}
