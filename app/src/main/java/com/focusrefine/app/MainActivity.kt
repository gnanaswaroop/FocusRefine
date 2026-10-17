package com.focusrefine.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.RECEIVER_NOT_EXPORTED
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    val defaultMode = remember { PromptManager.getDefaultModeName(context) }
    var currentMode by remember { mutableStateOf(prefs.getString("current_mode", defaultMode) ?: defaultMode) }
    var notifications by remember { mutableStateOf(emptyList<NotificationEntity>()) }
    var mutedCount by remember { mutableStateOf(0) }
    var urgentCount by remember { mutableStateOf(0) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var isFullScreenInbox by remember { mutableStateOf(false) }

    val totalCount = mutedCount + urgentCount
    val noiseFilteredPercent = if (totalCount > 0) (mutedCount * 100 / totalCount) else 0

    val modeOptions = listOf(
        Pair("Commute", "🚗"),
        Pair("Deep Focus", "🎧"),
        Pair("Sleep", "🌙"),
        Pair("Gaming", "🎮")
    )

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
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // 1. Top Bar Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(StudioColors.CardSurfaceHighlight),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.adaptive_foreground),
                        contentDescription = "App Logo",
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "FocusRefine",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_settings),
                        contentDescription = "Settings",
                        tint = StudioColors.TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Sub-badge: On-Device Protection Active
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
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
                            fontWeight = FontWeight.Medium,
                            color = StudioColors.GreenText
                        )
                    }
                }
            }

            // 2. Stat / Filter Summary Card
            StudioCard(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, StudioColors.StudioBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "${noiseFilteredPercent}% Noise Filtered",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${mutedCount} Muted • ${urgentCount} Urgent Shown Today",
                                fontSize = 12.sp,
                                color = StudioColors.TextSecondary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
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
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = StudioColors.ActiveViolet
                                )
                            )
                            Text(
                                text = if (isRunning) "AI Filtering Active" else "Filtering Paused",
                                fontSize = 10.sp,
                                color = if (isRunning) StudioColors.ActiveVioletLight else StudioColors.TextMuted
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Context Mode Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Context mode",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = StudioColors.TextSecondary
                )
                Text(
                    text = "$currentMode · Active",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = StudioColors.ActiveVioletLight
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(modeOptions) { (modeName, modeIcon) ->
                    val isSelected = currentMode.contains(modeName, ignoreCase = true) || (currentMode == modeName)
                    Surface(
                        onClick = {
                            currentMode = modeName
                            prefs.edit().putString("current_mode", modeName).apply()
                        },
                        shape = CircleShape,
                        color = if (isSelected) StudioColors.ActiveViolet else StudioColors.CardSurface,
                        border = BorderStroke(1.dp, if (isSelected) StudioColors.ActiveViolet else StudioColors.StudioBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = modeIcon, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = modeName,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Smart Inbox Section Header & Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Smart Inbox",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = { isFullScreenInbox = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Text(text = "⛶", fontSize = 14.sp, color = StudioColors.TextSecondary)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = { currentTabUrgent = false },
                        shape = CircleShape,
                        color = if (!currentTabUrgent) StudioColors.ActiveViolet else StudioColors.CardSurface,
                        border = BorderStroke(1.dp, StudioColors.StudioBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "🔕 Muted ($mutedCount)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    Surface(
                        onClick = { currentTabUrgent = true },
                        shape = CircleShape,
                        color = if (currentTabUrgent) StudioColors.GoldAccent else StudioColors.CardSurface,
                        border = BorderStroke(1.dp, StudioColors.StudioBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🔔 Shown ($urgentCount)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (currentTabUrgent) Color.Black else Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search notifications...", color = StudioColors.TextMuted, fontSize = 13.sp) },
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

            Spacer(modifier = Modifier.height(12.dp))

            // Notifications List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(notifications) { notif ->
                    val isMuted = !notif.isUrgent
                    StudioCard(
                        onClick = {
                            val intent = Intent(context, NotificationDetailActivity::class.java).apply {
                                putExtra("notif_id", notif.id)
                                putExtra("title", notif.title)
                                putExtra("text", notif.text)
                                putExtra("package_name", notif.packageName)
                                putExtra("timestamp", notif.timestamp)
                                putExtra("is_urgent", notif.isUrgent)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AppIconImage(
                                        packageName = notif.packageName,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = notif.title ?: "Notification",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                }
                                Text(
                                    text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(notif.timestamp)),
                                    fontSize = 11.sp,
                                    color = StudioColors.TextMuted
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = notif.text ?: "No content",
                                fontSize = 13.sp,
                                color = StudioColors.TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isMuted) StudioColors.RedSurface else StudioColors.GreenSurface,
                                    border = BorderStroke(1.dp, if (isMuted) StudioColors.RedAccent.copy(alpha = 0.3f) else StudioColors.GreenAccent.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = if (isMuted) "Muted: Routine Alert" else "High Urgency: Priority Prompt",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isMuted) StudioColors.RedText else StudioColors.GreenText,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = {
                                            val db = AppDatabase.getDatabase(context)
                                            val newUrgent = !notif.isUrgent
                                            val updated = notif.copy(isUrgent = newUrgent, isLearned = true)
                                            CoroutineScope(Dispatchers.IO).launch {
                                                db.notificationDao().insert(updated)
                                                refreshTrigger++
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, if (newUrgent) "Moved to Shown" else "Moved to Muted", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_swap_horiz),
                                            contentDescription = "Swap Tab",
                                            tint = StudioColors.TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            try {
                                                val intent = context.packageManager.getLaunchIntentForPackage(notif.packageName)
                                                if (intent != null) context.startActivity(intent)
                                                else Toast.makeText(context, "App not installed", Toast.LENGTH_SHORT).show()
                                            } catch (_: Exception) {
                                                Toast.makeText(context, "Cannot open app", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_open_in_new),
                                            contentDescription = "Open App",
                                            tint = StudioColors.TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    StudioFooter(
                        text = "🔒 Your notifications stay on this device.",
                        modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)
                    )
                }
            }
        }

        // Full Screen Smart Inbox Dialog
        if (isFullScreenInbox) {
            Dialog(
                onDismissRequest = { isFullScreenInbox = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = StudioColors.DarkBackground
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Smart Inbox (Full Screen)",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            IconButton(onClick = { isFullScreenInbox = false }) {
                                Text(text = "✕", fontSize = 18.sp, color = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                onClick = { currentTabUrgent = false },
                                shape = CircleShape,
                                modifier = Modifier.weight(1f),
                                color = if (!currentTabUrgent) StudioColors.ActiveViolet else StudioColors.CardSurface,
                                border = BorderStroke(1.dp, StudioColors.StudioBorder)
                            ) {
                                Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(text = "🔕 Muted ($mutedCount)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }

                            Surface(
                                onClick = { currentTabUrgent = true },
                                shape = CircleShape,
                                modifier = Modifier.weight(1f),
                                color = if (currentTabUrgent) StudioColors.GoldAccent else StudioColors.CardSurface,
                                border = BorderStroke(1.dp, StudioColors.StudioBorder)
                            ) {
                                Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "🔔 Shown ($urgentCount)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (currentTabUrgent) Color.Black else Color.White
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search notifications...", color = StudioColors.TextMuted, fontSize = 13.sp) },
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

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(notifications) { notif ->
                                val isMuted = !notif.isUrgent
                                StudioCard(
                                    onClick = {
                                        val intent = Intent(context, NotificationDetailActivity::class.java).apply {
                                            putExtra("notif_id", notif.id)
                                            putExtra("title", notif.title)
                                            putExtra("text", notif.text)
                                            putExtra("package_name", notif.packageName)
                                            putExtra("timestamp", notif.timestamp)
                                            putExtra("is_urgent", notif.isUrgent)
                                        }
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                AppIconImage(
                                                    packageName = notif.packageName,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = notif.title ?: "Notification",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = Color.White
                                                )
                                            }
                                            Text(
                                                text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(notif.timestamp)),
                                                fontSize = 11.sp,
                                                color = StudioColors.TextMuted
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Text(
                                            text = notif.text ?: "No content",
                                            fontSize = 13.sp,
                                            color = StudioColors.TextSecondary,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = if (isMuted) StudioColors.RedSurface else StudioColors.GreenSurface,
                                                border = BorderStroke(1.dp, if (isMuted) StudioColors.RedAccent.copy(alpha = 0.3f) else StudioColors.GreenAccent.copy(alpha = 0.3f))
                                            ) {
                                                Text(
                                                    text = if (isMuted) "Muted: Routine Alert" else "High Urgency: Priority Prompt",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isMuted) StudioColors.RedText else StudioColors.GreenText,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }

                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                IconButton(
                                                    onClick = {
                                                        val db = AppDatabase.getDatabase(context)
                                                        val newUrgent = !notif.isUrgent
                                                        val updated = notif.copy(isUrgent = newUrgent, isLearned = true)
                                                        CoroutineScope(Dispatchers.IO).launch {
                                                            db.notificationDao().insert(updated)
                                                            refreshTrigger++
                                                            withContext(Dispatchers.Main) {
                                                                Toast.makeText(context, if (newUrgent) "Moved to Shown" else "Moved to Muted", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        painter = painterResource(id = R.drawable.ic_swap_horiz),
                                                        contentDescription = "Swap Tab",
                                                        tint = StudioColors.TextSecondary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        try {
                                                            val intent = context.packageManager.getLaunchIntentForPackage(notif.packageName)
                                                            if (intent != null) context.startActivity(intent)
                                                            else Toast.makeText(context, "App not installed", Toast.LENGTH_SHORT).show()
                                                        } catch (_: Exception) {
                                                            Toast.makeText(context, "Cannot open app", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        painter = painterResource(id = R.drawable.ic_open_in_new),
                                                        contentDescription = "Open App",
                                                        tint = StudioColors.TextSecondary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                StudioFooter(
                                    text = "🔒 Your notifications stay on this device.",
                                    modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 800)
@Composable
fun MainDashboardPreview() {
    FocusRefineTheme {
        MainDashboardContent(
            onOpenSettings = {},
            onEnableNotificationAccess = {},
            isNotificationAccessEnabled = true,
            isFilteringActive = true
        )
    }
}
