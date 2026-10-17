package com.focusrefine.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("FocusRefinePrefs", MODE_PRIVATE)

        val isEmulator = android.os.Build.FINGERPRINT.contains("generic") ||
                         android.os.Build.HARDWARE.contains("goldfish") ||
                         android.os.Build.HARDWARE.contains("ranchu") ||
                         android.os.Build.MODEL.contains("sdk")

        setContent {
            FocusRefineTheme {
                SettingsStudioScreen(
                    isEmulator = isEmulator,
                    prefs = prefs,
                    onBack = { finish() },
                    onNavigateToAppRules = { startActivity(Intent(this, ConfigureModesActivity::class.java)) },
                    onNavigateToContextModes = { startActivity(Intent(this, ContextModesActivity::class.java)) },
                    onNavigateToPrivacy = { startActivity(Intent(this, PrivacyInsightsActivity::class.java)) },
                    onNavigateToLearnedRules = { startActivity(Intent(this, LearnedRulesActivity::class.java)) },
                    onSendSampleNotifications = {
                        lifecycleScope.launch(Dispatchers.IO) {
                            val dao = AppDatabase.getDatabase(this@SettingsActivity).notificationDao()
                            val inputStream = assets.open("samples.json")
                            val size = inputStream.available()
                            val buffer = ByteArray(size)
                            inputStream.read(buffer)
                            inputStream.close()
                            val jsonStr = String(buffer, Charsets.UTF_8)
                            val jsonArray = org.json.JSONArray(jsonStr)
                            val samples = mutableListOf<NotificationEntity>()
                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)
                                samples.add(
                                    NotificationEntity(
                                        title = obj.optString("title"),
                                        text = obj.optString("text"),
                                        packageName = obj.optString("packageName"),
                                        timestamp = System.currentTimeMillis() - (i * 1000L),
                                        isUrgent = obj.optBoolean("expectedUrgent", false)
                                    )
                                )
                            }
                            dao.deleteAll()
                            var mutedCount = 0
                            for (s in samples) {
                                dao.insert(s)
                                if (!s.isUrgent) mutedCount++
                            }
                            val currentSuppressed = prefs.getInt("suppressed_count", 0) + mutedCount
                            prefs.edit().putInt("suppressed_count", currentSuppressed).apply()
                            sendBroadcast(Intent(NotificationFilterService.ACTION_UPDATE_COUNT))
                            sendBroadcast(Intent(NotificationFilterService.ACTION_NOTIFICATION_ARRIVED).setPackage(packageName))
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@SettingsActivity, "✨ 20 sample notifications added!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onClearAllNotifications = {
                        lifecycleScope.launch(Dispatchers.IO) {
                            AppDatabase.getDatabase(this@SettingsActivity).notificationDao().deleteAll()
                            prefs.edit().putInt("suppressed_count", 0).apply()
                            sendBroadcast(Intent(NotificationFilterService.ACTION_UPDATE_COUNT))
                            sendBroadcast(Intent(NotificationFilterService.ACTION_NOTIFICATION_ARRIVED).setPackage(packageName))
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@SettingsActivity, "All inbox notifications cleared", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SettingsStudioScreen(
    isEmulator: Boolean,
    prefs: SharedPreferences?,
    onBack: () -> Unit,
    onNavigateToAppRules: () -> Unit,
    onNavigateToContextModes: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToLearnedRules: () -> Unit,
    onSendSampleNotifications: () -> Unit,
    onClearAllNotifications: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val defaultMode = remember { PromptManager.getDefaultModeName(context) }
    var currentMode by remember { mutableStateOf(prefs?.getString("current_mode", defaultMode) ?: defaultMode) }
    var filterStrengthIndex by remember { mutableStateOf(prefs?.getInt("filter_strength", 1) ?: 1) } // 0=Gentle, 1=Balanced, 2=Strict
    var navPromptsAllowed by remember { mutableStateOf(prefs?.getBoolean("exception_nav_prompts", true) ?: true) }
    var callsAllowed by remember { mutableStateOf(prefs?.getBoolean("exception_calls", true) ?: true) }
    var learnFromChoices by remember { mutableStateOf(prefs?.getBoolean("learn_from_choices", true) ?: true) }
    var useAiModel by remember { mutableStateOf(prefs?.getBoolean("use_ai_model", !isEmulator) ?: !isEmulator) }

    val modeOptions = listOf("Commute", "Deep Focus", "Sleep", "Gaming")

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
                    text = "Settings & Studio",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // 1. AI Engine Active Banner Card
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

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.Top) {
                        Text(text = "🛡️", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Processing stays on your device.",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = StudioColors.TextPrimary
                            )
                            Text(
                                text = "No cloud uploads. No notification history shared.",
                                fontSize = 11.sp,
                                color = StudioColors.TextMuted
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Filtering Strength Card
            StudioCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Filtering strength",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )

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

                    Spacer(modifier = Modifier.height(12.dp))

                    StudioSegmentedControl(
                        options = listOf("Gentle", "Balanced", "Strict"),
                        selectedIndex = filterStrengthIndex,
                        onOptionSelected = { idx ->
                            filterStrengthIndex = idx
                            prefs?.edit()?.putInt("filter_strength", idx)?.apply()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Urgent Notification Exceptions Card
            StudioCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Urgent notification exceptions",
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
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text(text = "🚀", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Navigation prompts", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Directions while you're on the move", fontSize = 11.sp, color = StudioColors.TextMuted)
                            }
                        }
                        Switch(
                            checked = navPromptsAllowed,
                            onCheckedChange = {
                                navPromptsAllowed = it
                                prefs?.edit()?.putBoolean("exception_nav_prompts", it)?.apply()
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = StudioColors.ActiveViolet)
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 10.dp), color = StudioColors.StudioBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text(text = "📞", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Calls & priority contacts", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Let important people reach you", fontSize = 11.sp, color = StudioColors.TextMuted)
                            }
                        }
                        Switch(
                            checked = callsAllowed,
                            onCheckedChange = {
                                callsAllowed = it
                                prefs?.edit()?.putBoolean("exception_calls", it)?.apply()
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = StudioColors.ActiveViolet)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Context Modes Card
            StudioCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Context modes",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Text(
                            text = "$currentMode · Active",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = StudioColors.ActiveVioletLight
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(modeOptions) { modeName ->
                            val isSelected = currentMode.contains(modeName, ignoreCase = true)
                            Surface(
                                onClick = {
                                    currentMode = modeName
                                    prefs?.edit()?.putString("current_mode", modeName)?.apply()
                                },
                                shape = CircleShape,
                                color = if (isSelected) StudioColors.ActiveViolet else StudioColors.CardSurfaceHighlight,
                                border = BorderStroke(1.dp, StudioColors.StudioBorder)
                            ) {
                                Text(
                                    text = modeName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Choose a mode to tune what gets through.",
                        fontSize = 11.sp,
                        color = StudioColors.TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Learn & Reset Card
            StudioCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
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
                                prefs?.edit()?.putBoolean("learn_from_choices", it)?.apply()
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = StudioColors.ActiveViolet)
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = StudioColors.StudioBorder)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                prefs?.edit()?.putInt("suppressed_count", 0)?.apply()
                                Toast.makeText(context, "Local learning data reset", Toast.LENGTH_SHORT).show()
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Reset local learning data", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = StudioColors.RedText)
                        Text("🔄", fontSize = 14.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 6. Navigation Hub Cards
            StudioCard(onClick = onNavigateToAppRules, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("App Rules", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                        Text("Per-app exceptions and filtering rules", fontSize = 11.sp, color = StudioColors.TextMuted)
                    }
                    Text("›", fontSize = 20.sp, color = StudioColors.TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            StudioCard(onClick = onNavigateToContextModes, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Context Modes", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                        Text("Manage mode definitions and rules", fontSize = 11.sp, color = StudioColors.TextMuted)
                    }
                    Text("›", fontSize = 20.sp, color = StudioColors.TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            StudioCard(onClick = onNavigateToPrivacy, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Privacy & Insights", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                        Text("On-device processing & privacy stats", fontSize = 11.sp, color = StudioColors.TextMuted)
                    }
                    Text("›", fontSize = 20.sp, color = StudioColors.TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Data Retention & Sample Testing Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSendSampleNotifications,
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, StudioColors.StudioBorder)
                ) {
                    Text("✨ Add 20 Samples", fontSize = 11.sp, color = Color.White)
                }

                OutlinedButton(
                    onClick = onClearAllNotifications,
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, StudioColors.RedAccent)
                ) {
                    Text("🗑 Clear Inbox", fontSize = 11.sp, color = StudioColors.RedText)
                }
            }

            StudioFooter(text = "Private by design. Refined by you.")
        }
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 800)
@Composable
fun SettingsStudioPreview() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)
    FocusRefineTheme {
        SettingsStudioScreen(
            isEmulator = false,
            prefs = prefs,
            onBack = {},
            onNavigateToAppRules = {},
            onNavigateToContextModes = {},
            onNavigateToPrivacy = {},
            onNavigateToLearnedRules = {},
            onSendSampleNotifications = {},
            onClearAllNotifications = {}
        )
    }
}

