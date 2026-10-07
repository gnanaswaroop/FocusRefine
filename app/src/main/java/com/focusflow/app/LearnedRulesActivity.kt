package com.focusflow.app

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

class LearnedRulesActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("FocusFlowPrefs", MODE_PRIVATE)

        setContent {
            MaterialTheme {
                var rules by remember { mutableStateOf(emptyList<NotificationEntity>()) }
                var refreshTrigger by remember { mutableStateOf(0) }

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
                                painter = painterResource(id = R.drawable.ic_focusflow_logo),
                                contentDescription = "App Logo",
                                modifier = Modifier.size(32.dp)
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
                                                color = if (rule.isUrgent) Color(0xFF2E7D32) else Color(0xFFC62828)
                                            )
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
                                                        refreshTrigger++
                                                        withContext(Dispatchers.Main) {
                                                            Toast.makeText(this@LearnedRulesActivity, "Rule deleted", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828))
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
                            onClick = {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    val db = AppDatabase.getDatabase(this@LearnedRulesActivity)
                                    db.notificationDao().deleteLearnedRules()
                                    refreshTrigger++
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(this@LearnedRulesActivity, "All learned rules cleared", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828))
                        ) {
                            Text("Clear All Learned Rules")
                        }
                    }
                }
            }
        }
    }
}
