package com.focusrefine.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.RECEIVER_NOT_EXPORTED
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import com.focusrefine.app.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var database: AppDatabase
    private var isAccessEnabled by mutableStateOf(false)
    private var isFilteringActive by mutableStateOf(true)
    private var lastThemeMode = -99
    private var liveRefreshTrigger by mutableStateOf(0)

    private val serviceStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == NotificationFilterService.ACTION_SERVICE_STATE_CHANGED) {
                val newState = intent.getBooleanExtra("is_running", prefs.getBoolean("service_running", true))
                isFilteringActive = newState
            } else if (intent.action == NotificationFilterService.ACTION_NOTIFICATION_ARRIVED) {
                liveRefreshTrigger++
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = AppDatabase.getDatabase(this)
        prefs = getSharedPreferences("FocusRefinePrefs", MODE_PRIVATE)
        lastThemeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        @Suppress("WrongConstant")
        AppCompatDelegate.setDefaultNightMode(lastThemeMode)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        isAccessEnabled = isNotificationServiceEnabled()
        isFilteringActive = prefs.getBoolean("service_running", true)
        setContent {
            FocusRefineTheme {
                MainDashboardContent(
                    onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onEnableNotificationAccess = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    isNotificationAccessEnabled = isAccessEnabled,
                    isFilteringActive = isFilteringActive,
                    liveRefreshTrigger = liveRefreshTrigger
                )
            }
        }

        val autoDetector = AndroidAutoDetector(this)
        autoDetector.observeConnection(this)
    }

    override fun onResume() {
        super.onResume()
        val currentThemeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        @Suppress("WrongConstant")
        AppCompatDelegate.setDefaultNightMode(currentThemeMode)
        if (lastThemeMode != -99 && currentThemeMode != lastThemeMode) {
            recreate()
            return
        }
        lastThemeMode = currentThemeMode

        isAccessEnabled = isNotificationServiceEnabled()
        liveRefreshTrigger++
        val filter = IntentFilter().apply {
            addAction(NotificationFilterService.ACTION_SERVICE_STATE_CHANGED)
            addAction(NotificationFilterService.ACTION_NOTIFICATION_ARRIVED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(serviceStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(serviceStateReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(serviceStateReceiver)
        } catch (_: Exception) {}
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(packageName)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardContent(
    onOpenSettings: () -> Unit,
    onEnableNotificationAccess: () -> Unit,
    isNotificationAccessEnabled: Boolean,
    isFilteringActive: Boolean = true,
    liveRefreshTrigger: Int = 0
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)
    val database = AppDatabase.getDatabase(context)

    var isRunning by remember(isFilteringActive) { mutableStateOf(isFilteringActive) }
    var currentTabUrgent by remember { mutableStateOf(false) } // false = Muted, true = Shown
    var searchQuery by remember { mutableStateOf("") }
    var currentMode by remember { mutableStateOf(prefs.getString("current_mode", "Commute") ?: "Commute") }
    var notifications by remember { mutableStateOf(emptyList<NotificationEntity>()) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // Load modes
    val modes = remember(refreshTrigger, liveRefreshTrigger) {
        val list = mutableListOf<String>()
        val ootbConfigs = PromptManager.getAllOotbConfigs(context)
        for ((name, config) in ootbConfigs) {
            if (prefs.getBoolean("mode_${name}_enabled", config.enabled)) list.add(name)
        }
        val customList = prefs.getString("custom_modes_list", "") ?: ""
        if (customList.isNotEmpty()) {
            for (name in customList.split(",")) {
                if (name.isNotBlank() && prefs.getBoolean("custom_mode_enabled_$name", true)) list.add(name)
            }
        }
        if (list.isEmpty()) list.add("Default")
        list
    }

    LaunchedEffect(isNotificationAccessEnabled) {
        if (!isNotificationAccessEnabled) {
            isRunning = false
            prefs.edit().putBoolean("service_running", false).apply()
        }
    }

    LaunchedEffect(currentTabUrgent, searchQuery, currentMode, refreshTrigger, liveRefreshTrigger) {
        withContext(Dispatchers.IO) {
            val dao = database.notificationDao()
            notifications = if (searchQuery.isEmpty()) {
                dao.getByUrgency(currentTabUrgent)
            } else {
                dao.searchByUrgency(currentTabUrgent, "%$searchQuery%")
            }
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
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.adaptive_foreground),
                    contentDescription = "App Logo",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Focus Refine",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = onOpenSettings) {
                    Text("Settings")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Notification Access Status
            if (!isNotificationAccessEnabled) {
                var showWhyDialog by remember { mutableStateOf(false) }

                Text(
                    text = "✕ Notification Access Disabled",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onEnableNotificationAccess,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Enable Access")
                    }
                    TextButton(
                        onClick = { showWhyDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Why is this required?")
                    }
                }

                if (showWhyDialog) {
                    AlertDialog(
                        onDismissRequest = { showWhyDialog = false },
                        title = { Text("Why Notification Access Is Required") },
                        text = {
                            Text(
                                "FocusRefine needs Notification Access to intercept, evaluate, and filter incoming notifications in real-time according to your active modes and learned rules.\n\nYour notifications are processed entirely on-device and never shared, stored externally, or uploaded."
                            )
                        },
                        confirmButton = {
                            Button(onClick = {
                                showWhyDialog = false
                                onEnableNotificationAccess()
                            }) {
                                Text("Got it, Enable Now")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showWhyDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            } else {
                Text(
                    text = "✓ Notification Access Enabled",
                    color = Color(0xFF2E7D32),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Controls Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Filtering Active",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = isRunning && isNotificationAccessEnabled,
                            enabled = isNotificationAccessEnabled,
                            onCheckedChange = { isChecked ->
                                if (!isNotificationAccessEnabled) {
                                    Toast.makeText(context, "Please enable Notification Access first", Toast.LENGTH_SHORT).show()
                                    isRunning = false
                                    prefs.edit().putBoolean("service_running", false).apply()
                                } else {
                                    isRunning = isChecked
                                    prefs.edit().putBoolean("service_running", isChecked).apply()
                                    val toggleIntent = Intent(NotificationFilterService.ACTION_TOGGLE_SERVICE).apply {
                                        setPackage(context.packageName)
                                    }
                                    context.sendBroadcast(toggleIntent)
                                }
                            }
                        )
                    }
                    Divider(modifier = Modifier.padding(vertical = 12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Current Mode",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        var expanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { expanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(currentMode)
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                modes.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode) },
                                        onClick = {
                                            currentMode = mode
                                            prefs.edit().putString("current_mode", mode).apply()
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Inbox Header & Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Inbox",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = {
                    refreshTrigger++
                    Toast.makeText(context, "Inbox refreshed", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_refresh),
                        contentDescription = "Refresh Inbox"
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { currentTabUrgent = false }) {
                    Text(
                        text = "Muted",
                        fontWeight = if (!currentTabUrgent) FontWeight.Bold else FontWeight.Normal,
                        color = if (!currentTabUrgent) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
                TextButton(onClick = { currentTabUrgent = true }) {
                    Text(
                        text = "Shown",
                        fontWeight = if (currentTabUrgent) FontWeight.Bold else FontWeight.Normal,
                        color = if (currentTabUrgent) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search notifications...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Notifications List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notifications) { notif ->
                    var isExpanded by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isExpanded = !isExpanded }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = notif.title ?: "No Title",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(notif.timestamp)),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = notif.text ?: "No Text",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = notif.packageName,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = {
                                    val db = AppDatabase.getDatabase(context)
                                    val newUrgent = !notif.isUrgent
                                    val updated = notif.copy(isUrgent = newUrgent, isLearned = true)
                                    CoroutineScope(Dispatchers.IO).launch {
                                        db.notificationDao().insert(updated)
                                        refreshTrigger++
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, if (newUrgent) "Moved to Shown tab" else "Moved to Muted tab", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }) {
                                    Icon(painter = painterResource(id = R.drawable.ic_swap_horiz), contentDescription = "Reclassify")
                                }
                                IconButton(onClick = {
                                    try {
                                        val intent = context.packageManager.getLaunchIntentForPackage(notif.packageName)
                                        if (intent != null) {
                                            context.startActivity(intent)
                                        } else {
                                            Toast.makeText(context, "App not installed: ${notif.packageName}", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Cannot open app", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(painter = painterResource(id = R.drawable.ic_open_in_new), contentDescription = "Open App")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
