package com.focusrefine.app

import android.content.Context
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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

class ConfigureModesActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences

    data class AppRuleItem(
        val appName: String,
        val packageName: String,
        val icon: String,
        val statusText: String,
        val ruleTypeIndex: Int // 0=Use AI, 1=Allow all, 2=Mute all
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)
        val themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        @Suppress("WrongConstant")
        AppCompatDelegate.setDefaultNightMode(themeMode)

        setContent {
            FocusRefineTheme {
                AppRulesScreen(
                    prefs = prefs,
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
fun AppRulesScreen(
    prefs: SharedPreferences,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var expandedAppPackage by remember { mutableStateOf("com.whatsapp") }

    val defaultMode = remember { PromptManager.getDefaultModeName(context) }
    val activeMode = remember { prefs.getString("current_mode", defaultMode) ?: defaultMode }

    var whatsappRuleType by remember { mutableStateOf(prefs.getInt("app_rule_type_whatsapp", 0)) }
    var whatsappMuteCasualChat by remember { mutableStateOf(prefs.getBoolean("app_mute_casual_whatsapp", true)) }
    var whatsappAllowUrgent by remember { mutableStateOf(prefs.getBoolean("app_allow_urgent_whatsapp", true)) }

    val initialAppRules = listOf(
        ConfigureModesActivity.AppRuleItem("Google Maps", "com.google.android.apps.maps", "🗺️", "Navigation prompts always allowed", 1),
        ConfigureModesActivity.AppRuleItem("WhatsApp", "com.whatsapp", "💬", "Casual chat muted in $activeMode", whatsappRuleType),
        ConfigureModesActivity.AppRuleItem("Gmail", "com.google.android.gm", "📧", "Filter routine mail; allow priority", 0),
        ConfigureModesActivity.AppRuleItem("Instagram", "com.instagram.android", "📷", "Mute social updates in $activeMode", 2)
    )

    val filteredRules = initialAppRules.filter {
        searchQuery.isEmpty() || it.appName.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = StudioColors.DarkBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                    text = "App Rules",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // 1. Active Mode Context Banner
            StudioCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Balanced filtering",
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

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "$activeMode is active. App rules tune what gets through; urgent exceptions stay allowed.",
                        fontSize = 12.sp,
                        color = StudioColors.TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search apps...", color = StudioColors.TextMuted, fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = StudioColors.CardSurface,
                    unfocusedContainerColor = StudioColors.CardSurface,
                    focusedBorderColor = StudioColors.ActiveViolet,
                    unfocusedBorderColor = StudioColors.StudioBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 3. App Rules List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredRules) { appRule ->
                    val isExpanded = expandedAppPackage == appRule.packageName

                    StudioCard(
                        onClick = {
                            expandedAppPackage = if (isExpanded) "" else appRule.packageName
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(StudioColors.CardSurfaceHighlight),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = appRule.icon, fontSize = 14.sp)
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = appRule.appName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = Color.White
                                        )
                                        Text(
                                            text = appRule.statusText,
                                            fontSize = 11.sp,
                                            color = if (appRule.statusText.contains("allowed", ignoreCase = true)) StudioColors.GreenText else StudioColors.TextSecondary
                                        )
                                    }
                                }

                                Text(
                                    text = if (isExpanded) "˅" else "›",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StudioColors.TextSecondary
                                )
                            }

                            // Expanded Tuner Drawer (e.g. for WhatsApp)
                            if (isExpanded) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = StudioColors.StudioBorder)

                                StudioCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    border = BorderStroke(1.dp, StudioColors.ActiveViolet.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${appRule.appName} rules",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = Color.White
                                            )

                                            Surface(
                                                shape = CircleShape,
                                                color = StudioColors.CardSurfaceHighlight,
                                                border = BorderStroke(1.dp, StudioColors.StudioBorder)
                                            ) {
                                                Text(
                                                    text = activeMode,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = StudioColors.ActiveVioletLight,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        StudioSegmentedControl(
                                            options = listOf("Use AI", "Allow all", "Mute all"),
                                            selectedIndex = whatsappRuleType,
                                            onOptionSelected = { idx ->
                                                whatsappRuleType = idx
                                                prefs.edit().putInt("app_rule_type_whatsapp", idx).apply()
                                                Toast.makeText(context, "${appRule.appName} rule set to: ${listOf("Use AI", "Allow all", "Mute all")[idx]}", Toast.LENGTH_SHORT).show()
                                            }
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Mute casual chat", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                Text("Coffee invites and everyday conversation", fontSize = 11.sp, color = StudioColors.TextMuted)
                                            }
                                            Switch(
                                                checked = whatsappMuteCasualChat,
                                                onCheckedChange = {
                                                    whatsappMuteCasualChat = it
                                                    prefs.edit().putBoolean("app_mute_casual_whatsapp", it).apply()
                                                },
                                                colors = SwitchDefaults.colors(checkedTrackColor = StudioColors.ActiveViolet)
                                            )
                                        }

                                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = StudioColors.StudioBorder)

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Allow urgent exceptions", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                Text("Calls & priority contacts still get through", fontSize = 11.sp, color = StudioColors.TextMuted)
                                            }
                                            Switch(
                                                checked = whatsappAllowUrgent,
                                                onCheckedChange = {
                                                    whatsappAllowUrgent = it
                                                    prefs.edit().putBoolean("app_allow_urgent_whatsapp", it).apply()
                                                },
                                                colors = SwitchDefaults.colors(checkedTrackColor = StudioColors.ActiveViolet)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            StudioFooter(text = "Private by design. Refined by you.")
        }
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 800)
@Composable
fun AppRulesPreview() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)
    FocusRefineTheme {
        AppRulesScreen(
            prefs = prefs,
            onBack = {}
        )
    }
}

