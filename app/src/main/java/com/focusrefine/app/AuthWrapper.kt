package com.focusrefine.app

import android.content.SharedPreferences
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Gates a screen behind the App Lock.
 *
 * - Fails closed: if the lock is enabled and biometrics/device credential are unavailable, the screen stays locked.
 * - Re-locks when the activity stops (leaves the screen), except while the system auth prompt is open.
 * - Prompts once per locked episode on resume; the Unlock button prompts again.
 * - Sets FLAG_SECURE while the lock is enabled (no screenshots, no content in Recents).
 * The lock state is an Activity field, so a rotation (activity recreation) asks again.
 */
@Composable
fun AuthWrapper(
    activity: FragmentActivity,
    prefs: SharedPreferences,
    isUnlocked: MutableState<Boolean>,
    showAuthError: MutableState<Boolean>,
    content: @Composable () -> Unit
) {
    val appLockEnabled = prefs.getBoolean("app_lock_enabled", false)
    val biometricAvailable = BiometricHelper.isBiometricAvailable(activity)
    val authInProgress = remember { mutableStateOf(false) }
    val locked = appLockEnabled && !isUnlocked.value

    DisposableEffect(appLockEnabled) {
        if (appLockEnabled) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {}
    }

    val promptUnlock: () -> Unit = {
        if (biometricAvailable && !authInProgress.value) {
            authInProgress.value = true
            BiometricHelper.showBiometricPrompt(
                activity,
                onSuccess = {
                    authInProgress.value = false
                    isUnlocked.value = true
                    showAuthError.value = false
                },
                onError = {
                    authInProgress.value = false
                    showAuthError.value = true
                }
            )
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, appLockEnabled) {
        var autoPrompted = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (appLockEnabled && !authInProgress.value) {
                    isUnlocked.value = false
                    autoPrompted = false
                }
                Lifecycle.Event.ON_RESUME -> if (appLockEnabled && !isUnlocked.value && !autoPrompted) {
                    autoPrompted = true
                    promptUnlock()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (locked) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = StudioColors.DarkBackground
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "FocusRefine is Locked",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (!biometricAvailable) {
                    Text("Biometric/Device authentication is not available. The app stays locked.", color = Color.Red)
                    Spacer(modifier = Modifier.height(16.dp))
                } else if (showAuthError.value) {
                    Text("Authentication failed. Please try again.", color = Color.Red)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Button(
                    onClick = promptUnlock,
                    colors = ButtonDefaults.buttonColors(containerColor = StudioColors.ActiveViolet)
                ) {
                    Text("Unlock")
                }
            }
        }
    } else {
        content()
    }
}
