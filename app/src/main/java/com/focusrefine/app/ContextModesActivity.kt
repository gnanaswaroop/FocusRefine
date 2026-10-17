package com.focusrefine.app

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class ContextModesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)
        val themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        @Suppress("WrongConstant")
        AppCompatDelegate.setDefaultNightMode(themeMode)

        setContent {
            FocusRefineTheme {
                ContextModesScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

data class ContextModeInfo(
    val name: String,
    val icon: String,
    val allowText: String,
    val muteText: String,
    val isActive: Boolean
)

@Composable
fun ContextModesScreen(
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)
    val defaultMode = remember { try { PromptManager.getDefaultModeName(context) } catch(_: Exception) { "Commute" } }
    var activeMode by remember { mutableStateOf(prefs.getString("current_mode", defaultMode) ?: defaultMode) }
    var manualSwitchOnly by remember { mutableStateOf(prefs.getBoolean("manual_mode_switch", true)) }

    val modeItems = listOf(
        ContextModeInfo("Commute", "🚗", "Navigation, calls & priority contacts", "Casual chat & social updates", activeMode.contains("Commute", ignoreCase = true)),
        ContextModeInfo("Deep Focus", "🎧", "Calls & priority contacts", "Routine mail, chat & social updates", activeMode.contains("Focus", ignoreCase = true)),
        ContextModeInfo("Sleep", "🌙", "Alarms, calls & priority contacts", "Routine alerts, chat & social updates", activeMode.contains("Sleep", ignoreCase = true)),
        ContextModeInfo("Gaming", "🎮", "Calls & priority contacts", "Routine mail & social updates", activeMode.contains("Gaming", ignoreCase = true))
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = StudioColors.DarkBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_refresh),
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Context Modes",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // 1. Top Active Mode Banner
            StudioCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🚗", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$activeMode · Active",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = StudioColors.ActiveViolet.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, StudioColors.ActiveViolet)
                        ) {
                            Text(
                                text = "AI ON",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = StudioColors.ActiveVioletLight,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Less chat, clearer directions. Your active mode decides which interruptions matter.",
                        fontSize = 12.sp,
                        color = StudioColors.TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Mode Cards List
            modeItems.forEach { modeInfo ->
                StudioCard(
                    onClick = {
                        activeMode = modeInfo.name
                        prefs.edit().putString("current_mode", modeInfo.name).apply()
                        Toast.makeText(context, "Active mode set to: ${modeInfo.name}", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    border = BorderStroke(1.dp, if (modeInfo.isActive) StudioColors.ActiveViolet else StudioColors.StudioBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = modeInfo.icon, fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = modeInfo.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (modeInfo.isActive) {
                                    Surface(
                                        shape = CircleShape,
                                        color = StudioColors.GreenSurface,
                                        border = BorderStroke(1.dp, StudioColors.GreenAccent.copy(alpha = 0.3f))
                                    ) {
                                        Text(
                                            text = "● Active",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = StudioColors.GreenText,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(text = "⚙️", fontSize = 14.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(text = "Allow", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StudioColors.GreenText, modifier = Modifier.width(44.dp))
                            Text(text = modeInfo.allowText, fontSize = 12.sp, color = StudioColors.TextSecondary)
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(text = "Mute", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StudioColors.RedText, modifier = Modifier.width(44.dp))
                            Text(text = modeInfo.muteText, fontSize = 12.sp, color = StudioColors.TextMuted)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Mode Activation Settings Card
            StudioCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Mode activation",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Switch modes manually", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("$activeMode stays active until you change it.", fontSize = 11.sp, color = StudioColors.TextMuted)
                        }
                        Switch(
                            checked = manualSwitchOnly,
                            onCheckedChange = {
                                manualSwitchOnly = it
                                prefs.edit().putBoolean("manual_mode_switch", it).apply()
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = StudioColors.ActiveViolet)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "No automatic context switching enabled.",
                        fontSize = 11.sp,
                        color = StudioColors.TextMuted
                    )
                }
            }

            StudioFooter(text = "Private by design. Refined by you.")
        }
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 800)
@Composable
fun ContextModesPreview() {
    FocusRefineTheme {
        ContextModesScreen(onBack = {})
    }
}

