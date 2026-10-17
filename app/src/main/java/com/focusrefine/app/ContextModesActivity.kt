package com.focusrefine.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val isCustom: Boolean = false
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
    var refreshTrigger by remember { mutableStateOf(0) }

    var showAddDialog by remember { mutableStateOf(false) }

    val modeItems = remember(refreshTrigger, activeMode) {
        val list = mutableListOf<ContextModeInfo>()
        list.add(ContextModeInfo("Commute", "🚗", "Navigation, calls & priority contacts", "Casual chat & social updates"))
        list.add(ContextModeInfo("Deep Focus", "🎧", "Calls & priority contacts", "Routine mail, chat & social updates"))
        list.add(ContextModeInfo("Sleep", "🌙", "Alarms, calls & priority contacts", "Routine alerts, chat & social updates"))
        list.add(ContextModeInfo("Gaming", "🎮", "Calls & priority contacts", "Routine mail & social updates"))

        val customStr = prefs.getString("custom_modes_list", "") ?: ""
        if (customStr.isNotEmpty()) {
            for (name in customStr.split(",")) {
                if (name.isNotBlank()) {
                    val prompt = prefs.getString("custom_mode_prompt_$name", "Custom rules") ?: "Custom rules"
                    list.add(ContextModeInfo(name, "⚡", "Priority contacts & matching keywords", "Routine alerts ($prompt)", isCustom = true))
                }
            }
        }
        list
    }

    val activeModeInfo = modeItems.find { it.name.equals(activeMode, ignoreCase = true) } ?: modeItems.first()

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
                        painter = painterResource(id = R.drawable.ic_arrow_back),
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
                            Text(text = activeModeInfo.icon, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${activeModeInfo.name} · Active",
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
                        text = "Your active mode decides which interruptions matter. Select any mode below to switch instantly.",
                        fontSize = 12.sp,
                        color = StudioColors.TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Mode Cards List
            modeItems.forEach { modeInfo ->
                val isActive = modeInfo.name.equals(activeMode, ignoreCase = true)

                StudioCard(
                    onClick = {
                        activeMode = modeInfo.name
                        prefs.edit().putString("current_mode", modeInfo.name).apply()
                        Toast.makeText(context, "Active mode set to: ${modeInfo.name}", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    border = BorderStroke(1.dp, if (isActive) StudioColors.ActiveViolet else StudioColors.StudioBorder)
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
                                if (isActive) {
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
                                }
                                if (modeInfo.isCustom) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = {
                                            val existing = prefs.getString("custom_modes_list", "") ?: ""
                                            val listCustom = existing.split(",").toMutableList()
                                            listCustom.remove(modeInfo.name)
                                            prefs.edit()
                                                .putString("custom_modes_list", listCustom.joinToString(","))
                                                .remove("custom_mode_prompt_${modeInfo.name}")
                                                .apply()
                                            if (activeMode.equals(modeInfo.name, ignoreCase = true)) {
                                                activeMode = "Commute"
                                                prefs.edit().putString("current_mode", "Commute").apply()
                                            }
                                            refreshTrigger++
                                            Toast.makeText(context, "Custom mode '${modeInfo.name}' deleted", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Text("🗑", fontSize = 12.sp, color = StudioColors.RedText)
                                    }
                                }
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

            // Add Custom Mode Action Button
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = StudioColors.ActiveViolet)
            ) {
                Text("➕ Add Custom Context Mode", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(14.dp))

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

        // Add Custom Mode Dialog
        if (showAddDialog) {
            var nameInput by remember { mutableStateOf("") }
            var promptInput by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Add Custom Context Mode", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Mode Name (e.g. Workout, Study)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = promptInput,
                            onValueChange = { promptInput = it },
                            label = { Text("Natural Language Rules / Prompt") },
                            placeholder = { Text("e.g., Fitness timer, health alerts, emergency calls") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val name = nameInput.trim()
                            val prompt = promptInput.trim()
                            if (name.isNotEmpty() && prompt.isNotEmpty()) {
                                val existing = prefs.getString("custom_modes_list", "") ?: ""
                                val customList = if (existing.isEmpty()) mutableListOf() else existing.split(",").toMutableList()
                                if (!customList.contains(name)) {
                                    customList.add(name)
                                    prefs.edit()
                                        .putString("custom_modes_list", customList.joinToString(","))
                                        .putString("custom_mode_prompt_$name", prompt)
                                        .putBoolean("custom_mode_enabled_$name", true)
                                        .apply()
                                    refreshTrigger++
                                    Toast.makeText(context, "Custom mode '$name' added!", Toast.LENGTH_SHORT).show()
                                    showAddDialog = false
                                } else {
                                    Toast.makeText(context, "Mode name already exists", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Name and rules cannot be empty", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StudioColors.ActiveViolet)
                    ) {
                        Text("Save Mode")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("Cancel", color = StudioColors.TextSecondary)
                    }
                },
                containerColor = StudioColors.CardSurface
            )
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
