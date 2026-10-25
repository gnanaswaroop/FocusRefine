@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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

class MainActivity : FragmentActivity() {

    private lateinit var prefs: SharedPreferences
    private var lastThemeMode = -99
    private var isNotifAccessEnabledState = mutableStateOf(false)
    private var isUnlocked = mutableStateOf(false)
    private var showAuthError = mutableStateOf(false)

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
                AuthWrapper(
                    activity = this@MainActivity,
                    prefs = prefs,
                    isUnlocked = isUnlocked,
                    showAuthError = showAuthError
                ) {
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
    }

    /** Re-reads the notification-access grant, starts the service if it is now allowed, and tells Home to reload. */
    private fun refreshAccess() {
        val isCurrentlyEnabled = isNotificationServiceEnabled()
        isNotifAccessEnabledState.value = isCurrentlyEnabled
        if (isCurrentlyEnabled) startNotificationService()
        sendBroadcast(Intent("com.focusrefine.app.ACTION_LIVE_REFRESH").setPackage(packageName))
    }

    // The system can update the grant a moment after we resume from its settings screen, so also watch the setting.
    private val accessObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refreshAccess()
    }

    override fun onStart() {
        super.onStart()
        contentResolver.registerContentObserver(Settings.Secure.getUriFor("enabled_notification_listeners"), false, accessObserver)
    }

    override fun onStop() {
        contentResolver.unregisterContentObserver(accessObserver)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Note: BiometricPrompt can launch separate activities (like PIN fallback) that pause this activity.
        // We do not blindly reset isUnlocked.value = false here, otherwise we create an infinite authentication loop
        // when returning from a successful system auth screen. We rely on the user explicitly closing the app,
        // or a more sophisticated foreground/background timeout logic if needed later.
        refreshAccess()

        val currentThemeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(currentThemeMode)
        if (lastThemeMode != -99 && currentThemeMode != lastThemeMode) {
            lastThemeMode = currentThemeMode
            recreate()
            return
        }
        lastThemeMode = currentThemeMode

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
        // startForeground re-posts the ongoing notification, so only start the service when it is not already running.
        if (isNotificationServiceEnabled() && !NotificationFilterService.isCreated) {
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

    // Reload whenever the screen comes back (e.g. from Settings after adding samples): a stopped activity does not
    // recompose, so a broadcast that arrived meanwhile is not enough on its own.
    DisposableEffect(context) {
        val owner = context as? androidx.lifecycle.LifecycleOwner
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) liveRefreshTrigger++
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

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
    var askLength by remember { mutableStateOf(false) }
    var showModeSheet by remember { mutableStateOf(false) }
    fun setFiltering(on: Boolean) {
        isRunning = on
        prefs.edit().putBoolean("service_running", on).apply()
        context.sendBroadcast(Intent(NotificationFilterService.ACTION_TOGGLE_SERVICE).setPackage(context.packageName))
    }
    var currentTabUrgent by remember { mutableStateOf(false) } // false = Muted, true = Shown
    var searchQuery by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) } // the search bar is hidden until the magnifier is tapped
    val defaultMode = remember { PromptManager.getDefaultModeName(context) }
    var currentMode by remember { mutableStateOf(prefs.getString("current_mode", defaultMode) ?: defaultMode) }
    var notifications by remember { mutableStateOf(emptyList<NotificationEntity>()) }
    var mutedCount by remember { mutableStateOf(0) }
    var urgentCount by remember { mutableStateOf(0) }
    var uncertainCount by remember { mutableStateOf(0) } // decisions the app was not sure about
    var reviewMode by remember { mutableStateOf(false) } // list only those, least sure first
    var refreshTrigger by remember { mutableStateOf(0) }

    // Timed session: re-read on every refresh and every 30 s so the countdown and the finish are picked up.
    var sessionNow by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            sessionNow = System.currentTimeMillis()
        }
    }
    // Ticks every second, only while a timed session runs, so the countdown beside the switch moves.
    var clockMs by remember { mutableStateOf(System.currentTimeMillis()) }
    val session = remember(refreshTrigger, liveRefreshTrigger, sessionNow) {
        ModeSession.refresh(context)
        ModeSession.current(context)
    }
    LaunchedEffect(session?.endMs) {
        while (session != null && !session.openEnded) {
            clockMs = System.currentTimeMillis()
            kotlinx.coroutines.delay(1_000)
        }
    }
    LaunchedEffect(session == null) { currentMode = prefs.getString("current_mode", currentMode) ?: currentMode }
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

    val currentModeIcon = modeOptions.firstOrNull { currentMode.contains(it.first, ignoreCase = true) || currentMode == it.first }?.second ?: "⚡"

    // A session that ends can pause filtering from outside this screen (alarm, notification button): follow the pref.
    LaunchedEffect(liveRefreshTrigger, refreshTrigger) {
        if (isNotificationAccessEnabled) isRunning = prefs.getBoolean("service_running", true)
    }

    if (askLength) {
        SessionLengthDialog(
            mode = currentMode,
            onPick = { minutes ->
                askLength = false
                setFiltering(true)
                ModeSession.start(context, currentMode, minutes, pauseWhenDone = true)
                refreshTrigger++
            },
            onDismiss = { askLength = false }
        )
    }

    LaunchedEffect(isNotificationAccessEnabled) {
        if (!isNotificationAccessEnabled) {
            // Remember that we paused it, so granting access again turns filtering back on (a pause the user chose stays).
            if (prefs.getBoolean("service_running", true)) prefs.edit().putBoolean("paused_for_access", true).apply()
            isRunning = false
            prefs.edit().putBoolean("service_running", false).apply()
        } else {
            if (prefs.getBoolean("paused_for_access", false)) {
                prefs.edit().putBoolean("service_running", true).putBoolean("paused_for_access", false).apply()
            }
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
            HomeHeader(onOpenHelp = onOpenHelp, onOpenSettings = onOpenSettings)

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
                                    } else if (isChecked) {
                                        askLength = true // filtering starts once a length is picked
                                    } else {
                                        // Turning it off ends any "work for this long" session without pausing twice.
                                        if (session != null) ModeSession.end(context, pauseFiltering = false)
                                        setFiltering(false)
                                        refreshTrigger++
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

                    // The mode you are filtering for, with the time left when a timed session runs. Tap it to change mode.
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showModeSheet = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = currentModeIcon, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = currentMode,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (session != null && isRunning) {
                            // A timed session: the countdown, or a note when it has no end.
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (session.openEnded) "until you turn it off"
                                else ModeSession.countdown(session.endMs, maxOf(clockMs, session.startMs)) + " left",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = StudioColors.ActiveVioletLight,
                                maxLines = 1
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        StudioNavChevron(tint = StudioColors.TextSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (showModeSheet) {
                ModeChooserSheet(
                    modes = modeOptions,
                    current = currentMode,
                    summaryOf = { PromptManager.summaryFor(context, it) ?: "Uses the prompt you wrote for it in Edit modes." },
                    onPick = { name ->
                        currentMode = name
                        prefs.edit().putString("current_mode", name).apply()
                        refreshTrigger++
                        showModeSheet = false
                    },
                    onManage = {
                        showModeSheet = false
                        context.startActivity(Intent(context, ContextModesActivity::class.java))
                    },
                    onDismiss = { showModeSheet = false }
                )
            }

            // 4. Smart Inbox Section Header & Tabs
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
                    modifier = Modifier.size(28.dp).semantics { contentDescription = "Open full screen inbox" }
                ) {
                    Text(text = "⛶", fontSize = 14.sp, color = StudioColors.TextSecondary)
                }
                IconButton(
                    onClick = {
                        if (searchOpen) searchQuery = ""
                        searchOpen = !searchOpen
                    },
                    modifier = Modifier.size(28.dp).semantics { contentDescription = if (searchOpen) "Close search" else "Search notifications" }
                ) {
                    Text(text = if (searchOpen) "✕" else "🔍", fontSize = 14.sp, color = StudioColors.TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // The pills wrap onto a second line on narrow screens instead of squeezing their labels.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = { reviewMode = false; currentTabUrgent = false },
                    shape = CircleShape,
                    color = if (!reviewMode && !currentTabUrgent) StudioColors.ActiveViolet else StudioColors.CardSurface,
                    border = BorderStroke(1.dp, StudioColors.StudioBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🔕 Muted ($mutedCount)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                    }
                }

                Surface(
                    onClick = { reviewMode = false; currentTabUrgent = true },
                    shape = CircleShape,
                    color = if (!reviewMode && currentTabUrgent) StudioColors.GoldAccent else StudioColors.CardSurface,
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
                            color = if (!reviewMode && currentTabUrgent) Color.Black else Color.White,
                            maxLines = 1
                        )
                    }
                }

                if (uncertainCount > 0 || reviewMode) {
                    CloseCallsChip(count = uncertainCount, active = reviewMode, onClick = { reviewMode = !reviewMode })
                }
            }

            if (searchOpen) {
                val searchFocus = remember { FocusRequester() }
                LaunchedEffect(Unit) { searchFocus.requestFocus() }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search notifications...", color = StudioColors.TextMuted, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
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
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Close calls: the chip above is the way in; the banner only appears while reviewing, to say so and to end it
            if (reviewMode) {
                ReviewBanner(count = uncertainCount, reviewing = true, onToggle = { reviewMode = false })
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

                            IconButton(
                                onClick = { isFullScreenInbox = false },
                                modifier = Modifier.semantics { contentDescription = "Close full screen inbox" }
                            ) {
                                Text(text = "✕", fontSize = 18.sp, color = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                onClick = { reviewMode = false; currentTabUrgent = false },
                                shape = CircleShape,
                                modifier = Modifier.weight(1f),
                                color = if (!reviewMode && !currentTabUrgent) StudioColors.ActiveViolet else StudioColors.CardSurface,
                                border = BorderStroke(1.dp, StudioColors.StudioBorder)
                            ) {
                                Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(text = "🔕 Muted ($mutedCount)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }

                            Surface(
                                onClick = { reviewMode = false; currentTabUrgent = true },
                                shape = CircleShape,
                                modifier = Modifier.weight(1f),
                                color = if (!reviewMode && currentTabUrgent) StudioColors.GoldAccent else StudioColors.CardSurface,
                                border = BorderStroke(1.dp, StudioColors.StudioBorder)
                            ) {
                                Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "🔔 Shown ($urgentCount)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (!reviewMode && currentTabUrgent) Color.Black else Color.White
                                    )
                                }
                            }

                            if (uncertainCount > 0 || reviewMode) {
                                CloseCallsChip(count = uncertainCount, active = reviewMode, onClick = { reviewMode = !reviewMode }, modifier = Modifier.weight(1f))
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

/** Bottom sheet opened from the mode line on Home: each enabled mode, what it lets through, and a way to pick it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeChooserSheet(
    modes: List<Pair<String, String>>,
    current: String,
    summaryOf: (String) -> String,
    onPick: (String) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = StudioColors.CardSurface) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Text("Choose a mode", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                "A mode decides which notifications still get through. Each one is sorted by the AI, and anything it is unsure about goes to Review.",
                fontSize = 12.sp, color = StudioColors.TextSecondary, lineHeight = 17.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            modes.forEach { (name, icon) ->
                val selected = current.contains(name, ignoreCase = true) || current == name
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) StudioColors.ActiveViolet.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { onPick(name) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(text = icon, fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(summaryOf(name), fontSize = 12.sp, color = StudioColors.TextSecondary, lineHeight = 17.sp)
                    }
                    if (selected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Active", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StudioColors.ActiveVioletLight)
                    }
                }
            }
            TextButton(onClick = onManage) {
                Text("Edit modes and prompts", color = StudioColors.ActiveVioletLight)
            }
        }
    }
}
