package com.focusrefine.app

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import com.focusrefine.app.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class ConfigureModesActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences
    private var lastThemeMode = -99

    data class ModeItem(val name: String, val prompt: String, val isEnabled: Boolean, val isCustom: Boolean)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("FocusRefinePrefs", MODE_PRIVATE)
        lastThemeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(lastThemeMode)

        setContent {
            FocusRefineTheme {
                var refreshTrigger by remember { mutableStateOf(0) }
                val modes = remember(refreshTrigger) { loadModes() }

                var showAddDialog by remember { mutableStateOf(false) }
                var editingMode by remember { mutableStateOf<ModeItem?>(null) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Top Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_focusrefine_logo),
                                contentDescription = "App Logo",
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Configure Modes",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Button(onClick = { finish() }) {
                                Text("Back")
                            }
                        }

                        Text(
                            text = "Manage your filtering modes, prompts, and active rules below.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Modes List
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(modes) { mode ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { editingMode = mode }
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = mode.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Switch(
                                                checked = mode.isEnabled,
                                                onCheckedChange = { isChecked ->
                                                    if (!mode.isCustom) {
                                                        prefs.edit().putBoolean("mode_${mode.name}_enabled", isChecked).apply()
                                                    } else {
                                                        prefs.edit().putBoolean("custom_mode_enabled_${mode.name}", isChecked).apply()
                                                    }
                                                    refreshTrigger++
                                                    Toast.makeText(this@ConfigureModesActivity, "${mode.name} enabled = $isChecked", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = mode.prompt,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (mode.isCustom) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedButton(
                                                onClick = {
                                                    val existing = prefs.getString("custom_modes_list", "") ?: ""
                                                    val listCustom = existing.split(",").toMutableList()
                                                    listCustom.remove(mode.name)
                                                    prefs.edit()
                                                        .putString("custom_modes_list", listCustom.joinToString(","))
                                                        .remove("custom_mode_prompt_${mode.name}")
                                                        .remove("custom_mode_enabled_${mode.name}")
                                                        .apply()
                                                    refreshTrigger++
                                                    Toast.makeText(this@ConfigureModesActivity, "Mode '${mode.name}' deleted", Toast.LENGTH_SHORT).show()
                                                },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828))
                                            ) {
                                                Text("Delete Mode", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Add New Custom Mode")
                        }
                    }

                    // Dialog for Adding / Editing Mode
                    if (showAddDialog || editingMode != null) {
                        var nameInput by remember { mutableStateOf(editingMode?.name ?: "") }
                        var promptInput by remember { mutableStateOf(editingMode?.prompt ?: "") }
                        val isEditing = editingMode != null

                        AlertDialog(
                            onDismissRequest = {
                                showAddDialog = false
                                editingMode = null
                            },
                            title = { Text(if (isEditing) "Edit Prompt: ${editingMode?.name}" else "Add Custom Mode") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (!isEditing || editingMode?.isCustom == true) {
                                        OutlinedTextField(
                                            value = nameInput,
                                            onValueChange = { nameInput = it },
                                            label = { Text("Mode Name") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    OutlinedTextField(
                                        value = promptInput,
                                        onValueChange = { promptInput = it },
                                        label = { Text("Natural Language Prompt / Rules") },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 3
                                    )
                                }
                            },
                            confirmButton = {
                                Button(onClick = {
                                    val name = nameInput.trim()
                                    val prompt = promptInput.trim()
                                    if (name.isNotEmpty() && prompt.isNotEmpty()) {
                                        if (!isEditing) {
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
                                                Toast.makeText(this@ConfigureModesActivity, "Mode '$name' added", Toast.LENGTH_SHORT).show()
                                                showAddDialog = false
                                            } else {
                                                Toast.makeText(this@ConfigureModesActivity, "Mode name already exists", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            val targetName = editingMode!!.name
                                            if (editingMode!!.isCustom) {
                                                prefs.edit().putString("custom_mode_prompt_$targetName", prompt).apply()
                                            } else {
                                                prefs.edit().putString("prompt_$targetName", prompt).apply()
                                            }
                                            refreshTrigger++
                                            Toast.makeText(this@ConfigureModesActivity, "Prompt for '$targetName' updated", Toast.LENGTH_SHORT).show()
                                            editingMode = null
                                        }
                                    } else {
                                        Toast.makeText(this@ConfigureModesActivity, "Name and prompt cannot be empty", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Text("Save")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    showAddDialog = false
                                    editingMode = null
                                }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun loadModes(): List<ModeItem> {
        val list = mutableListOf<ModeItem>()
        val ootbConfigs = PromptManager.getAllOotbConfigs(this)
        for ((name, config) in ootbConfigs) {
            val enabled = prefs.getBoolean("mode_${name}_enabled", config.enabled)
            val prompt = prefs.getString("prompt_${name}", config.prompt) ?: config.prompt
            list.add(ModeItem(name, prompt, enabled, false))
        }

        val customStr = prefs.getString("custom_modes_list", "") ?: ""
        if (customStr.isNotEmpty()) {
            for (name in customStr.split(",")) {
                if (name.isNotBlank()) {
                    val prompt = prefs.getString("custom_mode_prompt_$name", "") ?: ""
                    val enabled = prefs.getBoolean("custom_mode_enabled_$name", true)
                    list.add(ModeItem(name, prompt, enabled, true))
                }
            }
        }
        return list
    }

    override fun onResume() {
        super.onResume()
        val currentThemeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(currentThemeMode)
        if (lastThemeMode != -99 && currentThemeMode != lastThemeMode) {
            lastThemeMode = currentThemeMode
            recreate()
            return
        }
        lastThemeMode = currentThemeMode
    }
}
