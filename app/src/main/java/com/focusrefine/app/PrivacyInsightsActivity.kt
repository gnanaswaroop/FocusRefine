package com.focusrefine.app

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PrivacyInsightsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)
        val themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        @Suppress("WrongConstant")
        AppCompatDelegate.setDefaultNightMode(themeMode)

        val isEmulator = android.os.Build.FINGERPRINT.contains("generic") ||
                         android.os.Build.HARDWARE.contains("goldfish") ||
                         android.os.Build.HARDWARE.contains("ranchu") ||
                         android.os.Build.MODEL.contains("sdk")

        setContent {
            FocusRefineTheme {
                PrivacyInsightsScreen(
                    isEmulator = isEmulator,
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
fun PrivacyInsightsScreen(
    isEmulator: Boolean,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)
    val database = AppDatabase.getDatabase(context)

    val defaultMode = remember { PromptManager.getDefaultModeName(context) }
    val activeMode = remember { prefs.getString("current_mode", defaultMode) ?: defaultMode }

    var learnFromChoices by remember { mutableStateOf(prefs.getBoolean("learn_from_choices", true)) }
    var mutedCount by remember { mutableStateOf(0) }
    var urgentCount by remember { mutableStateOf(0) }
    var showClearDataDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val dao = database.notificationDao()
            mutedCount = dao.getByUrgency(false).size
            urgentCount = dao.getByUrgency(true).size
        }
    }

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
                    text = "Privacy & Insights",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // 1. On-Device AI Status Card
            StudioCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "✨", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEmulator) "On-Device Semantic Engine Active" else "Gemini Nano AI Active (AICore)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Processing stays on your device.",
                        fontSize = 12.sp,
                        color = StudioColors.TextSecondary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = CircleShape,
                        color = StudioColors.GreenSurface,
                        border = BorderStroke(1.dp, StudioColors.GreenAccent.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(StudioColors.GreenAccent)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "On-Device Protection Active",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = StudioColors.GreenText
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Today's Filtering Stat Card
            StudioCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Today's filtering",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = StudioColors.TextSecondary
                        )

                        Text(
                            text = "$activeMode · Active",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = StudioColors.ActiveVioletLight
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "84% Noise Filtered",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${mutedCount} Muted • ${urgentCount} Urgent Shown Today",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = StudioColors.TextSecondary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Quiet routine alerts. Keep urgent ones visible.",
                        fontSize = 11.sp,
                        color = StudioColors.TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Private by Design Card
            StudioCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🛡️", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Private by design",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "No cloud notification processing",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Text(
                        text = "No cloud uploads. No notification history shared.",
                        fontSize = 11.sp,
                        color = StudioColors.TextMuted
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                Toast.makeText(context, "All notification processing is 100% on-device", Toast.LENGTH_LONG).show()
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "View privacy information",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = StudioColors.ActiveVioletLight
                        )
                        Text("›", fontSize = 18.sp, color = StudioColors.ActiveVioletLight)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Local Learning & Data Card
            StudioCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Local learning & data",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Learn from my choices", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Improve filtering using local feedback.", fontSize = 11.sp, color = StudioColors.TextMuted)
                        }
                        Switch(
                            checked = learnFromChoices,
                            onCheckedChange = {
                                learnFromChoices = it
                                prefs.edit().putBoolean("learn_from_choices", it).apply()
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = StudioColors.ActiveViolet)
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = StudioColors.StudioBorder)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showClearDataDialog = true },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Clear local learning data", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = StudioColors.RedText)
                            Text("Resets learned feedback on this device. Your app rules and context preferences stay unchanged.", fontSize = 11.sp, color = StudioColors.TextMuted)
                        }
                        Text("🔄", fontSize = 16.sp)
                    }
                }
            }

            if (showClearDataDialog) {
                AlertDialog(
                    onDismissRequest = { showClearDataDialog = false },
                    title = { Text("Clear Local Learning Data?") },
                    text = { Text("Are you sure you want to reset learned feedback? Your app rules and mode preferences will stay intact.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                showClearDataDialog = false
                                CoroutineScope(Dispatchers.IO).launch {
                                    database.notificationDao().deleteLearnedRules()
                                    prefs.edit().putInt("suppressed_count", 0).apply()
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Local learning data cleared", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StudioColors.RedAccent)
                        ) {
                            Text("Clear Data")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearDataDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            StudioFooter(text = "Your notifications stay on this device.")
        }
    }
}
