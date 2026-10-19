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
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences
    private var lastThemeMode = -99
    private var isNotifAccessEnabledState = mutableStateOf(false)

    private val notifReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == NotificationFilterService.ACTION_NOTIFICATION_ARRIVED ||
                intent.action == NotificationFilterService.ACTION_SERVICE_STATE_CHANGED) {
                val liveRefreshIntent = Intent("com.focusrefine.app.ACTION_LIVE_REFRESH")
                sendBroadcast(liveRefreshIntent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("FocusRefinePrefs", MODE_PRIVATE)
        lastThemeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        @Suppress("WrongConstant")
        AppCompatDelegate.setDefaultNightMode(lastThemeMode)

        val filter = IntentFilter().apply {
            addAction(NotificationFilterService.ACTION_NOTIFICATION_ARRIVED)
            addAction(NotificationFilterService.ACTION_SERVICE_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notifReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(notifReceiver, filter)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        isNotifAccessEnabledState.value = isNotificationServiceEnabled()
        startNotificationService()

        setContent {
            FocusRefineTheme {
                MainStudioScreen(
                    isNotificationAccessEnabled = isNotifAccessEnabledState.value,
                    isFilteringActive = prefs.getBoolean("service_running", true),
                    prefs = prefs,
                    onEnableNotificationAccess = {
                        try {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        } catch (_: Exception) {
                            Toast.makeText(this, "Unable to open notification settings", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onOpenHelp = { startActivity(Intent(this, HelpActivity::class.java)) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val isCurrentlyEnabled = isNotificationServiceEnabled()
        isNotifAccessEnabledState.value = isCurrentlyEnabled

        if (isCurrentlyEnabled) {
            startNotificationService()
        }

        val currentThemeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(currentThemeMode)
        if (lastThemeMode != -99 && currentThemeMode != lastThemeMode) {
            lastThemeMode = currentThemeMode
            recreate()
            return
        }
        lastThemeMode = currentThemeMode

        val liveRefreshIntent = Intent("com.focusrefine.app.ACTION_LIVE_REFRESH")
        sendBroadcast(liveRefreshIntent)
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(notifReceiver)
        } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (flat != null) {
            val names = flat.split(":")
            for (name in names) {
                if (name.contains(pkgName)) return true
            }
        }
        return false
    }

    private fun startNotificationService() {
        if (isNotificationServiceEnabled()) {
            val serviceIntent = Intent(this, NotificationFilterService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainStudioScreen(
    isNotificationAccessEnabled: Boolean,
    isFilteringActive: Boolean,
    prefs: SharedPreferences,
    onEnableNotificationAccess: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit = {}
) {
    val context = LocalContext.current
    var liveRefreshTrigger by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                liveRefreshTrigger++
            }
        }
        val filter = IntentFilter("com.focusrefine.app.ACTION_LIVE_REFRESH")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }

    val database = AppDatabase.getDatabase(context)

    var isRunning by remember(isFilteringActive) { mutableStateOf(isFilteringActive) }
    var currentTabUrgent by remember { mutableStateOf(false) } // false = Muted, true = Shown
    var searchQuery by remember { mutableStateOf("") }
    val defaultMode = remember { PromptManager.getDefaultModeName(context) }
    var currentMode by remember { mutableStateOf(prefs.getString("current_mode", defaultMode) ?: defaultMode) }
    var notifications by remember { mutableStateOf(emptyList<NotificationEntity>()) }
    var mutedCount by remember { mutableStateOf(0) }
    var urgentCount by remember { mutableStateOf(0) }
    var uncertainCount by remember { mutableStateOf(0) } // decisions the app was not sure about
    var reviewMode by remember { mutableStateOf(false) } // list only those, least sure first
    var refreshTrigger by remember { mutableStateOf(0) }
    var isFullScreenInbox by remember { mutableStateOf(false) }

    val enableSwipeAnimations = remember(refreshTrigger, liveRefreshTrigger) {
        prefs.getBoolean("enable_swipe_animations", true)
    }

    val totalCount = mutedCount + urgentCount
    val noiseFilteredPercent = if (totalCount > 0) (mutedCount * 100 / totalCount) else 0

    // Context modes enabled on home page: Default enabled ONLY for Commute and Deep Focus
    val modeOptions = remember(refreshTrigger, liveRefreshTrigger) {
        val list = mutableListOf<Pair<String, String>>()
        if (prefs.getBoolean("mode_enabled_Commute", true)) list.add(Pair("Commute", "🚗"))
        if (prefs.getBoolean("mode_enabled_Deep Focus", true)) list.add(Pair("Deep Focus", "🎧"))
        if (prefs.getBoolean("mode_enabled_Sleep", false)) list.add(Pair("Sleep", "🌙"))
        if (prefs.getBoolean("mode_enabled_Gaming", false)) list.add(Pair("Gaming", "🎮"))

        val customStr = prefs.getString("custom_modes_list", "") ?: ""
        if (customStr.isNotEmpty()) {
            for (name in customStr.split(",")) {
                if (name.isNotBlank() && !list.any { it.first.equals(name, ignoreCase = true) }) {
                    if (prefs.getBoolean("custom_mode_enabled_$name", false)) {
                        list.add(Pair(name, "⚡"))
                    }
                }
            }
        }
        if (list.isEmpty()) {
            list.add(Pair("Commute", "🚗"))
            list.add(Pair("Deep Focus", "🎧"))
        }
        list
    }

    LaunchedEffect(isNotificationAccessEnabled) {
        if (!isNotificationAccessEnabled) {
            isRunning = false
            prefs.edit().putBoolean("service_running", false).apply()
        } else {
            isRunning = prefs.getBoolean("service_running", true)
        }
    }

    LaunchedEffect(currentTabUrgent, searchQuery, currentMode, refreshTrigger, liveRefreshTrigger, reviewMode) {
        withContext(Dispatchers.IO) {
            val dao = database.notificationDao()
            notifications = if (reviewMode) {
                dao.getUncertain(Confidence.UNCERTAIN_BELOW).filter {
                    searchQuery.isEmpty() || (it.title ?: "").contains(searchQuery, ignoreCase = true) || (it.text ?: "").contains(searchQuery, ignoreCase = true)
                }
            } else if (searchQuery.isEmpty()) {
                dao.getByUrgency(currentTabUrgent)
            } else {
                dao.searchByUrgency(currentTabUrgent, "%$searchQuery%")
            }
            mutedCount = dao.getByUrgency(false).size
            urgentCount = dao.getByUrgency(true).size
            uncertainCount = dao.countUncertain(Confidence.UNCERTAIN_BELOW)
            if (reviewMode && uncertainCount == 0) reviewMode = false
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
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(StudioColors.CardSurfaceHighlight)
                        .border(1.dp, StudioColors.ActiveViolet.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.adaptive_foreground),
                        contentDescription = "App Logo",
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "FocusRefine",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = StudioColors.GreenSurface,
                    border = BorderStroke(1.dp, StudioColors.GreenAccent.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(StudioColors.GreenAccent)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "On Device AI",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = StudioColors.GreenText
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onOpenHelp,
                    modifier = Modifier.size(32.dp).semantics { contentDescription = "Help" }
                ) {
                    Text(text = "?", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = StudioColors.TextPrimary)
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_settings),
                        contentDescription = "Settings",
                        tint = StudioColors.TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Notification Access Status Warning & Educational Dialog
            if (!isNotificationAccessEnabled) {
                var showWhyDialog by remember { mutableStateOf(false) }

                StudioCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    border = BorderStroke(1.dp, StudioColors.RedAccent)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "⚠️", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Notification Access Disabled",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = StudioColors.RedText
                                )
                            }

                            Surface(
                                shape = CircleShape,
                                color = StudioColors.RedSurface,
                                border = BorderStroke(1.dp, StudioColors.RedAccent.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = "Action Required",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StudioColors.RedText,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "FocusRefine needs Notification Access to intercept, evaluate, and filter incoming notifications in real-time.",
                            fontSize = 12.sp,
                            color = StudioColors.TextSecondary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onEnableNotificationAccess,
                                modifier = Modifier.weight(1f),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = StudioColors.RedAccent)
                            ) {
                                Text("Approve Permission", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { showWhyDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = CircleShape,
                                border = BorderStroke(1.dp, StudioColors.StudioBorder)
                            ) {
                                Text("Why Required?", fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }
                }

                if (showWhyDialog) {
                    AlertDialog(
                        onDismissRequest = { showWhyDialog = false },
                        title = { Text("Why Notification Access Is Required", color = Color.White, fontWeight = FontWeight.Bold) },
                        text = {
                            Text(
                                "FocusRefine acts as an on-device privacy shield for your notification shade.\n\n" +
                                "To classify and quiet routine pings, Android requires explicit Notification Access.\n\n" +
                                "• 100% On-Device: Your text never leaves your phone.\n" +
                                "• Zero Internet Permission: We physically cannot transmit your data.",
                                color = StudioColors.TextSecondary,
                                fontSize = 13.sp
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showWhyDialog = false
                                    onEnableNotificationAccess()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = StudioColors.ActiveViolet)
                            ) {
                                Text("Enable Access Now")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showWhyDialog = false }) {
                                Text("Cancel", color = StudioColors.TextMuted, fontSize = 12.sp)
                            }
                        },
                        containerColor = StudioColors.CardSurface
                    )
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
                                enabled = true,
                                onCheckedChange = { isChecked ->
                                    if (!isNotificationAccessEnabled) {
                                        Toast.makeText(context, "Please enable Notification Access first", Toast.LENGTH_SHORT).show()
                                        onEnableNotificationAccess()
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

            // 3. Context Mode Selector (Long press opens ContextModesActivity)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Context mode (Long press to manage)",
                    fontSize = 12.sp,
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
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isSelected) StudioColors.ActiveViolet else StudioColors.CardSurface)
                            .border(1.dp, if (isSelected) StudioColors.ActiveViolet else StudioColors.StudioBorder, CircleShape)
                            .combinedClickable(
                                onClick = {
                                    currentMode = modeName
                                    prefs.edit().putString("current_mode", modeName).apply()
                                    refreshTrigger++
                                },
                                onLongClick = {
                                    context.startActivity(Intent(context, ContextModesActivity::class.java))
                                    Toast.makeText(context, "Opening details for $modeName", Toast.LENGTH_SHORT).show()
                                }
                            )
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
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

            // Close calls: surfaced above the list so they are not buried in a long inbox
            if (uncertainCount > 0 || reviewMode) {
                ReviewBanner(count = uncertainCount, reviewing = reviewMode, onToggle = { reviewMode = !reviewMode })
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Notifications List with Swipe Gesture & Animated Tab Transitions
            var totalDrag by remember { mutableStateOf(0f) }

            if (enableSwipeAnimations) {
                AnimatedContent(
                    targetState = currentTabUrgent,
                    transitionSpec = {
                        if (targetState) { // Swipe Left -> Shown tab
                            (slideInHorizontally { width -> width } + fadeIn()).togetherWith(slideOutHorizontally { width -> -width } + fadeOut())
                        } else { // Swipe Right -> Muted tab
                            (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(slideOutHorizontally { width -> width } + fadeOut())
                        }
                    },
                    label = "SmartInboxSwipeAnimation",
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) { isUrgentTab ->
                    key(isUrgentTab) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragStart = { totalDrag = 0f },
                                    onDragEnd = {
                                        if (totalDrag < -80f && !currentTabUrgent) {
                                            currentTabUrgent = true
                                            Toast.makeText(context, "Switched to Shown tab", Toast.LENGTH_SHORT).show()
                                        } else if (totalDrag > 80f && currentTabUrgent) {
                                            currentTabUrgent = false
                                            Toast.makeText(context, "Switched to Muted tab", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onHorizontalDrag = { _, dragAmount ->
                                        totalDrag += dragAmount
                                    }
                                )
                            },
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(notifications, key = { it.id }) { notif ->
                            InboxNotificationCard(notif = notif, onChanged = { refreshTrigger++ })
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
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { totalDrag = 0f },
                                onDragEnd = {
                                    if (totalDrag < -80f && !currentTabUrgent) {
                                        currentTabUrgent = true
                                        Toast.makeText(context, "Switched to Shown tab", Toast.LENGTH_SHORT).show()
                                    } else if (totalDrag > 80f && currentTabUrgent) {
                                        currentTabUrgent = false
                                        Toast.makeText(context, "Switched to Muted tab", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    totalDrag += dragAmount
                                }
                            )
                        },
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(notifications, key = { it.id }) { notif ->
                        InboxNotificationCard(notif = notif, onChanged = { refreshTrigger++ })
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

                        var fullScreenDrag by remember { mutableStateOf(0f) }

                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures(
                                        onDragStart = { fullScreenDrag = 0f },
                                        onDragEnd = {
                                            if (fullScreenDrag < -80f && !currentTabUrgent) {
                                                currentTabUrgent = true
                                            } else if (fullScreenDrag > 80f && currentTabUrgent) {
                                                currentTabUrgent = false
                                            }
                                        },
                                        onHorizontalDrag = { _, dragAmount ->
                                            fullScreenDrag += dragAmount
                                        }
                                    )
                                },
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(notifications, key = { it.id }) { notif ->
                                InboxNotificationCard(notif = notif, onChanged = { refreshTrigger++ })
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
fun MainStudioPreview() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)
    FocusRefineTheme {
        MainStudioScreen(
            isNotificationAccessEnabled = true,
            isFilteringActive = true,
            prefs = prefs,
            onEnableNotificationAccess = {},
            onOpenSettings = {}
        )
    }
}
