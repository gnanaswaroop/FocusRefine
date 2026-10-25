package com.focusrefine.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : FragmentActivity() {

    private lateinit var prefs: SharedPreferences
    private var isReclassifying by mutableStateOf(false)
    private var isUnlocked = mutableStateOf(false)
    private var showAuthError = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("FocusRefinePrefs", MODE_PRIVATE)

        val engineLoaded = prefs.getBoolean(EmbeddingClassifier.PREF_ENGINE_LOADED, false)

        setContent {
            FocusRefineTheme {
                AuthWrapper(
                    activity = this@SettingsActivity,
                    prefs = prefs,
                    isUnlocked = isUnlocked,
                    showAuthError = showAuthError
                ) {
                    SettingsStudioScreen(
                    engineLoaded = engineLoaded,
                    prefs = prefs,
                    onBack = { finish() },
                    onNavigateToAppRules = { startActivity(Intent(this, ConfigureModesActivity::class.java)) },
                    onNavigateToContextModes = { startActivity(Intent(this, ContextModesActivity::class.java)) },
                    onNavigateToPrivacy = { startActivity(Intent(this, PrivacyInsightsActivity::class.java)) },
                    onNavigateToHelp = { startActivity(Intent(this, HelpActivity::class.java)) },
                    onNavigateToChangelog = { startActivity(Intent(this, ChangelogActivity::class.java)) },
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
                    isReclassifying = isReclassifying,
                    onReclassifyHistory = {
                        if (!isReclassifying) {
                            isReclassifying = true
                            lifecycleScope.launch(Dispatchers.IO) {
                                val message = try {
                                    val s = HistoryReclassifier.run(this@SettingsActivity)
                                    when {
                                        s.total == 0 -> "No notification history to re-classify"
                                        s.changed == 0 -> "Re-checked ${s.total} notifications, no changes"
                                        else -> "Re-classified ${s.total}: ${s.toShown} now Shown, ${s.toMuted} now Muted"
                                    } + if (s.failed > 0) " (${s.failed} failed)" else ""
                                } catch (e: Exception) {
                                    "Re-classification failed: ${e.message}"
                                }
                                withContext(Dispatchers.Main) {
                                    isReclassifying = false
                                    Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_LONG).show()
                                }
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
}

@Composable
fun SettingsStudioScreen(
    engineLoaded: Boolean,
    prefs: SharedPreferences?,
    onBack: () -> Unit,
    onNavigateToAppRules: () -> Unit,
    onNavigateToContextModes: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToHelp: () -> Unit = {},
    onNavigateToChangelog: () -> Unit = {},
    onNavigateToLearnedRules: () -> Unit,
    onSendSampleNotifications: () -> Unit,
    onClearAllNotifications: () -> Unit,
    isReclassifying: Boolean = false,
    onReclassifyHistory: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showReclassifyDialog by remember { mutableStateOf(false) }
    val defaultMode = remember { PromptManager.getDefaultModeName(context) }
    var currentMode by remember { mutableStateOf(prefs?.getString("current_mode", defaultMode) ?: defaultMode) }
    var filterStrengthIndex by remember { mutableStateOf(prefs?.getInt("filter_strength", 1) ?: 1) } // 0=Gentle, 1=Balanced, 2=Strict
    var learnFromChoices by remember { mutableStateOf(prefs?.getBoolean("learn_from_choices", true) ?: true) }
    var useAiModel by remember { mutableStateOf(prefs?.getBoolean("use_ai_model", true) ?: true) }
    var enableSwipeAnimations by remember { mutableStateOf(prefs?.getBoolean("enable_swipe_animations", true) ?: true) }
    var appLockEnabled by remember { mutableStateOf(prefs?.getBoolean("app_lock_enabled", BiometricHelper.isBiometricAvailable(context)) ?: false) }

    // Collapsible card states (all collapsed when the screen opens)
    var isEngineExpanded by remember { mutableStateOf(false) }
    var isStrengthExpanded by remember { mutableStateOf(false) }
    var isExceptionsExpanded by remember { mutableStateOf(false) }
    var isModesExpanded by remember { mutableStateOf(false) }
    var isLearnExpanded by remember { mutableStateOf(false) }
    var isAdvancedExpanded by remember { mutableStateOf(false) }

    val modeOptions = listOf("Commute", "Deep Focus", "Sleep", "Gaming")

    if (showReclassifyDialog) {
        AlertDialog(
            onDismissRequest = { showReclassifyDialog = false },
            title = { Text("Re-run classification?") },
            text = {
                Text(
                    "Every notification in your history will be re-checked using the current mode ($currentMode), " +
                        "filtering strength, exceptions and learned rules. Items may move between Shown and Muted. " +
                        "Your learned rules are not changed, and nothing is added to or removed from your notification shade."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showReclassifyDialog = false
                    onReclassifyHistory()
                }) { Text("Re-run") }
            },
            dismissButton = { TextButton(onClick = { showReclassifyDialog = false }) { Text("Cancel") } }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = StudioColors.DarkBackground
    ) {
        val scrollState = rememberScrollState()
        var menuOpen by remember { mutableStateOf(false) }
        Column(modifier = Modifier.fillMaxSize()) {
            // The title bar is outside the scrolling column, so Back and the menu stay in view while the content scrolls.
            StudioTopBar(title = "Settings", onBack = onBack, showDivider = scrollState.value > 0) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_more_vert),
                            contentDescription = "More options",
                            tint = Color.White
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Help & how it works") },
                            onClick = { menuOpen = false; onNavigateToHelp() }
                        )
                        DropdownMenuItem(
                            text = { Text("What's new") },
                            onClick = { menuOpen = false; onNavigateToChangelog() }
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
            ) {
            // 1. Classification Engine Toggle Card (Collapsible)
            StudioCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { isEngineExpanded = !isEngineExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Classification Engine",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        StudioChevron(expanded = isEngineExpanded)
                    }

                    if (isEngineExpanded) {
                        Spacer(modifier = Modifier.height(10.dp))

                        // Active Engine Status Banner: reflects the engine the service actually loaded
                        val engineActive = useAiModel && engineLoaded
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (engineActive) StudioColors.ActiveViolet.copy(alpha = 0.2f) else StudioColors.CardSurfaceHighlight,
                            border = BorderStroke(1.dp, if (engineActive) StudioColors.ActiveViolet else StudioColors.StudioBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (engineActive) "✨" else "🔍",
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (engineActive) "On-Device AI Engine Active" else "On-Device Keyword Matcher Active",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (engineActive) StudioColors.ActiveVioletLight else Color.White
                                    )
                                    Text(
                                        text = if (engineActive)
                                            "Semantic matching with an on-device embedding model for built-in modes. Custom modes use your prompt keywords."
                                        else if (!useAiModel)
                                            "AI engine is off. Matching the keywords in your mode prompts."
                                        else
                                            "The AI model is not loaded (the service may not have started yet, or this device cannot run it). Matching prompt keywords.",
                                        fontSize = 11.sp,
                                        color = if (useAiModel && !engineLoaded) StudioColors.GoldAccent else StudioColors.TextMuted
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Use On-Device AI Engine",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Toggle off to use the keyword matcher. Takes effect when the filter service restarts.",
                                    fontSize = 11.sp,
                                    color = StudioColors.TextMuted
                                )
                            }

                            Switch(
                                checked = useAiModel,
                                onCheckedChange = { isChecked ->
                                    useAiModel = isChecked
                                    prefs?.edit()?.putBoolean("use_ai_model", isChecked)?.apply()
                                    Toast.makeText(
                                        context,
                                        "Engine set to: ${if (isChecked) "On-Device AI" else "Keyword Matcher"}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = StudioColors.ActiveViolet,
                                    disabledUncheckedTrackColor = StudioColors.CardSurfaceHighlight,
                                    disabledUncheckedThumbColor = StudioColors.TextMuted
                                )
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = StudioColors.StudioBorder)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🛡️", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Processing stays 100% on your device.",
                                    fontSize = 12.sp,
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
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Filtering Strength Card (Collapsible)
            StudioCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { isStrengthExpanded = !isStrengthExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Filtering strength",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = StudioColors.ActiveViolet.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, StudioColors.ActiveViolet)
                            ) {
                                Text(
                                    text = listOf("Gentle", "Balanced", "Strict")[filterStrengthIndex],
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StudioColors.ActiveVioletLight,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            StudioChevron(expanded = isStrengthExpanded)
                        }
                    }

                    if (isStrengthExpanded) {
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
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Urgent Notification Exceptions Card (Collapsible)
            val exceptionsList = remember { ExceptionManager.getAllExceptions(context) }

            StudioCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { isExceptionsExpanded = !isExceptionsExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Urgent notification exceptions",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        StudioChevron(expanded = isExceptionsExpanded)
                    }

                    if (isExceptionsExpanded) {
                        Spacer(modifier = Modifier.height(12.dp))

                        exceptionsList.forEachIndexed { index, exc ->
                            var isChecked by remember {
                                mutableStateOf(prefs?.getBoolean(exc.prefKey, exc.defaultEnabled) ?: exc.defaultEnabled)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text(text = exc.icon, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(exc.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text(exc.description, fontSize = 11.sp, color = StudioColors.TextMuted)
                                    }
                                }
                                Switch(
                                    checked = isChecked,
                                    onCheckedChange = { newValue ->
                                        isChecked = newValue
                                        prefs?.edit()?.putBoolean(exc.prefKey, newValue)?.apply()
                                    },
                                    colors = SwitchDefaults.colors(checkedTrackColor = StudioColors.ActiveViolet)
                                )
                            }

                            if (index < exceptionsList.size - 1) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = StudioColors.StudioBorder)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Unified Context Modes Card (Collapsible)
            StudioCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { isModesExpanded = !isModesExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Context modes",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = StudioColors.ActiveViolet.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, StudioColors.ActiveViolet)
                            ) {
                                Text(
                                    text = "$currentMode · Active",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StudioColors.ActiveVioletLight,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            StudioChevron(expanded = isModesExpanded)
                        }
                    }

                    if (isModesExpanded) {
                        Spacer(modifier = Modifier.height(12.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(modeOptions) { modeName ->
                                val isSelected = currentMode.equals(modeName, ignoreCase = true)
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

                        Spacer(modifier = Modifier.height(10.dp))

                        val modeSummaryText = when {
                            currentMode.contains("Commute", ignoreCase = true) ->
                                "Allows navigation, transit alerts & priority calls; quiets casual chat."
                            currentMode.contains("Focus", ignoreCase = true) ->
                                "Allows urgent calls, boss pings & deadlines; quiets routine social alerts."
                            currentMode.contains("Sleep", ignoreCase = true) ->
                                "Allows emergency alerts & family calls; quiets all non-critical notifications."
                            currentMode.contains("Gaming", ignoreCase = true) ->
                                "Allows calls & priority contacts; quiets routine emails & social pings."
                            else ->
                                "Filtering tailored to your custom mode rules."
                        }

                        Text(
                            text = modeSummaryText,
                            fontSize = 11.sp,
                            color = StudioColors.TextSecondary
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = StudioColors.StudioBorder)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToContextModes() },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Manage mode definitions & custom modes", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = StudioColors.ActiveVioletLight)
                                Text("Custom prompts, allow/mute rule summaries", fontSize = 11.sp, color = StudioColors.TextMuted)
                            }
                            StudioNavChevron(tint = StudioColors.ActiveVioletLight)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            // 5. Learning Card (Collapsible)
            StudioCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { isLearnExpanded = !isLearnExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Learning",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        StudioChevron(expanded = isLearnExpanded)
                    }

                    if (isLearnExpanded) {
                        Spacer(modifier = Modifier.height(12.dp))

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

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = StudioColors.StudioBorder)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToLearnedRules() },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("View & Manage Learned Rules", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = StudioColors.ActiveVioletLight)
                                Text("Review AI rules generated from your feedback", fontSize = 11.sp, color = StudioColors.TextMuted)
                            }
                            StudioNavChevron(tint = StudioColors.ActiveVioletLight)
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = StudioColors.StudioBorder)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isReclassifying) { showReclassifyDialog = true },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Re-run classification on history", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = StudioColors.ActiveVioletLight)
                                Text(
                                    if (isReclassifying) "Re-classifying your history…" else "Re-check past notifications with your current mode, settings and rules",
                                    fontSize = 11.sp,
                                    color = StudioColors.TextMuted
                                )
                            }
                            if (isReclassifying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = StudioColors.ActiveViolet
                                )
                            } else {
                                Text("↻", fontSize = 18.sp, color = StudioColors.ActiveVioletLight)
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = StudioColors.StudioBorder)

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
                            Icon(
                                painter = painterResource(id = R.drawable.ic_reset_learning),
                                contentDescription = null,
                                tint = StudioColors.RedText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            StudioCard(onClick = onNavigateToAppRules, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("App Rules", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                        Text("Per-app exceptions and filtering rules", fontSize = 11.sp, color = StudioColors.TextMuted)
                    }
                    StudioNavChevron()
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            StudioCard(onClick = onNavigateToPrivacy, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Privacy & Insights", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                        Text("On-device processing & privacy stats", fontSize = 11.sp, color = StudioColors.TextMuted)
                    }
                    StudioNavChevron()
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 6. Advanced Settings Card (Collapsible)
            StudioCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { isAdvancedExpanded = !isAdvancedExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Advanced Settings",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        StudioChevron(expanded = isAdvancedExpanded)
                    }

                    if (isAdvancedExpanded) {
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val newVal = !appLockEnabled
                                    if (newVal && !BiometricHelper.isBiometricAvailable(context)) {
                                        Toast.makeText(context, "Biometric authentication is not available or not set up on this device.", Toast.LENGTH_LONG).show()
                                    } else {
                                        appLockEnabled = newVal
                                        prefs?.edit()?.putBoolean("app_lock_enabled", newVal)?.apply()
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("App Lock (Biometrics)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Require fingerprint or face unlock to view notifications.", fontSize = 11.sp, color = StudioColors.TextMuted)
                            }
                            Switch(
                                checked = appLockEnabled,
                                onCheckedChange = {
                                    if (it && !BiometricHelper.isBiometricAvailable(context)) {
                                        Toast.makeText(context, "Biometric authentication is not available or not set up on this device.", Toast.LENGTH_LONG).show()
                                    } else {
                                        appLockEnabled = it
                                        prefs?.edit()?.putBoolean("app_lock_enabled", it)?.apply()
                                    }
                                },
                                colors = SwitchDefaults.colors(checkedTrackColor = StudioColors.ActiveViolet)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Inbox Swipe Animations", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Animate tab transitions on Smart Inbox swipe.", fontSize = 11.sp, color = StudioColors.TextMuted)
                            }
                            Switch(
                                checked = enableSwipeAnimations,
                                onCheckedChange = {
                                    enableSwipeAnimations = it
                                    prefs?.edit()?.putBoolean("enable_swipe_animations", it)?.apply()
                                },
                                colors = SwitchDefaults.colors(checkedTrackColor = StudioColors.ActiveViolet)
                            )
                        }
                    }
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
}

@Preview(showBackground = true, widthDp = 380, heightDp = 800)
@Composable
fun SettingsStudioPreview() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)
    FocusRefineTheme {
        SettingsStudioScreen(
            engineLoaded = true,
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
