package com.focusrefine.app

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LearnedRulesActivity : FragmentActivity() {

    private lateinit var prefs: SharedPreferences
    private var lastThemeMode = -99
    private var isUnlocked = mutableStateOf(false)
    private var showAuthError = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("FocusRefinePrefs", MODE_PRIVATE)
        lastThemeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        @Suppress("WrongConstant")
        AppCompatDelegate.setDefaultNightMode(lastThemeMode)

        setContent {
            FocusRefineTheme {
                var rules by remember { mutableStateOf(emptyList<NotificationEntity>()) }
                var refreshTrigger by remember { mutableStateOf(0) }
                var showClearConfirmDialog by remember { mutableStateOf(false) }

                LaunchedEffect(refreshTrigger) {
                    withContext(Dispatchers.IO) {
                        val db = AppDatabase.getDatabase(this@LearnedRulesActivity)
                        rules = db.notificationDao().getLearnedRules()
                    }
                }

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
                                painter = painterResource(id = R.drawable.adaptive_foreground),
                                contentDescription = "App Logo",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Learned Rules",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Button(onClick = { finish() }) {
                                Text("Back")
                            }
                        }

                        Text(
                            text = "Review rules learned from your swipes and reclassifications. You can correct or delete any rule.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Rules List
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(rules) { rule ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${rule.title ?: "App"}: ${rule.text ?: ""}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = if (rule.isUrgent) "Status: Shown" else "Status: Hidden",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = if (rule.isUrgent) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                                            )
                                        }

                                        if (!rule.synthesizedRule.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Surface(
                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                            ) {
                                                Text(
                                                    text = "✨ AI Rule: ${rule.synthesizedRule}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    lifecycleScope.launch(Dispatchers.IO) {
                                                        val db = AppDatabase.getDatabase(this@LearnedRulesActivity)
                                                        val updated = rule.copy(isUrgent = !rule.isUrgent)
                                                        db.notificationDao().insert(updated)
                                                        refreshTrigger++
                                                        withContext(Dispatchers.Main) {
                                                            Toast.makeText(this@LearnedRulesActivity, "Rule updated", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Toggle Status", fontSize = 12.sp)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    lifecycleScope.launch(Dispatchers.IO) {
                                                        val db = AppDatabase.getDatabase(this@LearnedRulesActivity)
                                                        db.notificationDao().delete(rule)
                                                        PersonalLearning.forget(this@LearnedRulesActivity, rule.title, rule.text)
                                                        refreshTrigger++
                                                        withContext(Dispatchers.Main) {
                                                            Toast.makeText(this@LearnedRulesActivity, "Rule deleted", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                            ) {
                                                Text("Delete", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { showClearConfirmDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Clear All Learned Rules")
                        }

                        if (showClearConfirmDialog) {
                            AlertDialog(
                                onDismissRequest = { showClearConfirmDialog = false },
                                title = { Text("Clear All Learned Rules?") },
                                text = { Text("Are you sure you want to clear all learned rules? FocusRefine will reset its learned preferences.") },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            showClearConfirmDialog = false
                                            lifecycleScope.launch(Dispatchers.IO) {
                                                val db = AppDatabase.getDatabase(this@LearnedRulesActivity)
                                                db.notificationDao().deleteLearnedRules()
                                                PersonalLearning.clear(this@LearnedRulesActivity)
                                                refreshTrigger++
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(this@LearnedRulesActivity, "All learned rules cleared", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text("Clear All")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showClearConfirmDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
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
