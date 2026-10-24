package com.focusrefine.app

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import android.view.WindowManager

@Composable
fun AuthWrapper(
    activity: FragmentActivity,
    prefs: android.content.SharedPreferences,
    isUnlocked: MutableState<Boolean>,
    showAuthError: MutableState<Boolean>,
    content: @Composable () -> Unit
) {
    val appLockEnabled = prefs.getBoolean("app_lock_enabled", false)
    val biometricAvailable = BiometricHelper.isBiometricAvailable(activity)

    // Treat "enabled but unavailable" as locked out to fail securely,
    // rather than failing open. (In a real app, we'd provide a recovery flow,
    // but this addresses the PR comment regarding "fail open").
    val requiresAuth = appLockEnabled

    if (requiresAuth && !isUnlocked.value) {
        // Apply FLAG_SECURE to prevent screenshots/screen recording while locked
        DisposableEffect(Unit) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            onDispose {
                if (isUnlocked.value) {
                    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }

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
                     Text("Biometric/Device authentication is not available.", color = Color.Red)
                     Spacer(modifier = Modifier.height(16.dp))
                } else if (showAuthError.value) {
                    Text("Authentication failed. Please try again.", color = Color.Red)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Button(
                    onClick = {
                        if (biometricAvailable) {
                            BiometricHelper.showBiometricPrompt(
                                activity,
                                onSuccess = {
                                    isUnlocked.value = true
                                    showAuthError.value = false
                                    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                                },
                                onError = {
                                    showAuthError.value = true
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StudioColors.ActiveViolet)
                ) {
                    Text("Unlock")
                }
            }
        }

        if (biometricAvailable) {
            LaunchedEffect(Unit) {
                BiometricHelper.showBiometricPrompt(
                    activity,
                    onSuccess = {
                        isUnlocked.value = true
                        showAuthError.value = false
                        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    },
                    onError = {
                        showAuthError.value = true
                    }
                )
            }
        }
    } else {
        DisposableEffect(Unit) {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            onDispose {}
        }
        content()
    }
}
