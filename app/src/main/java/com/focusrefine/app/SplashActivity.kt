package com.focusrefine.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("FocusRefinePrefs", MODE_PRIVATE)
        val savedThemeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(savedThemeMode)
        super.onCreate(savedInstanceState)

        setContent {
            FocusRefineTheme {
                val scale = remember { Animatable(0.7f) }
                val alpha = remember { Animatable(0f) }

                LaunchedEffect(Unit) {
                    scale.animateTo(
                        targetValue = 1.0f,
                        animationSpec = tween(durationMillis = 600)
                    )
                    alpha.animateTo(
                        targetValue = 1.0f,
                        animationSpec = tween(durationMillis = 600)
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = StudioColors.DarkBackground
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .padding(24.dp)
                                .scale(scale.value)
                                .alpha(alpha.value)
                        ) {
                            // Studio Animated Logo Badge
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(StudioColors.CardSurfaceHighlight)
                                    .border(2.dp, StudioColors.ActiveViolet, RoundedCornerShape(28.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.adaptive_foreground),
                                    contentDescription = "FocusRefine Logo",
                                    modifier = Modifier.size(90.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Text(
                                text = "FocusRefine",
                                fontSize = 30.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Surface(
                                shape = CircleShape,
                                color = StudioColors.ActiveViolet.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, StudioColors.ActiveViolet)
                            ) {
                                Text(
                                    text = "✨ On-Device AI Privacy Shield",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StudioColors.ActiveVioletLight,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Filter the noise, focus on what truly matters",
                                fontSize = 13.sp,
                                color = StudioColors.TextSecondary
                            )

                            Spacer(modifier = Modifier.height(36.dp))

                            CircularProgressIndicator(
                                color = StudioColors.ActiveViolet,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Bottom Version & Privacy Footer
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                fontSize = 11.sp,
                                color = StudioColors.TextMuted
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "🔒 100% Private On-Device Processing",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = StudioColors.GreenText
                            )
                        }
                    }
                }
            }
        }

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 1300)
    }
}
