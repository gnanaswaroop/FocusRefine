package com.focusrefine.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class NotificationDetailActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)
        val themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        @Suppress("WrongConstant")
        AppCompatDelegate.setDefaultNightMode(themeMode)

        val notifId = intent.getIntExtra("notif_id", -1)
        val title = intent.getStringExtra("title") ?: "Notification"
        val text = intent.getStringExtra("text") ?: "No message body"
        val packageName = intent.getStringExtra("package_name") ?: "com.app"
        val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
        val initialUrgent = intent.getBooleanExtra("is_urgent", false)

        setContent {
            FocusRefineTheme {
                NotificationDetailScreen(
                    notifId = notifId,
                    title = title,
                    text = text,
                    packageName = packageName,
                    timestamp = timestamp,
                    initialUrgent = initialUrgent,
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
fun NotificationDetailScreen(
    notifId: Int,
    title: String,
    text: String,
    packageName: String,
    timestamp: Long,
    initialUrgent: Boolean,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)
    val database = AppDatabase.getDatabase(context)
    val defaultMode = remember { PromptManager.getDefaultModeName(context) }
    val activeMode = remember { prefs.getString("current_mode", defaultMode) ?: defaultMode }

    var isUrgent by remember { mutableStateOf(initialUrgent) }
    var learnFromChoice by remember { mutableStateOf(true) }
    var feedbackSubmitted by remember { mutableStateOf<Boolean?>(null) }

    val appLabel = when {
        packageName.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
        packageName.contains("maps", ignoreCase = true) -> "Google Maps"
        packageName.contains("gmail", ignoreCase = true) -> "Gmail"
        packageName.contains("instagram", ignoreCase = true) -> "Instagram"
        packageName.contains("slack", ignoreCase = true) -> "Slack"
        else -> packageName.substringAfterLast(".").replaceFirstChar { it.uppercase() }
    }

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
                        painter = painterResource(id = R.drawable.ic_refresh),
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Notification Details",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // 1. Notification Card
            StudioCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIconImage(
                                packageName = packageName,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = appLabel,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                        }
                        Text(
                            text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timestamp)),
                            fontSize = 11.sp,
                            color = StudioColors.TextMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        shape = CircleShape,
                        color = if (!isUrgent) StudioColors.RedSurface else StudioColors.GreenSurface,
                        border = BorderStroke(1.dp, if (!isUrgent) StudioColors.RedAccent.copy(alpha = 0.3f) else StudioColors.GreenAccent.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = if (!isUrgent) "Muted: Chat Distraction" else "High Urgency: Priority Prompt",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!isUrgent) StudioColors.RedText else StudioColors.GreenText,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (!isUrgent) "Muted in Smart Inbox · Not shown as an alert" else "Allowed in Smart Inbox · Displayed as alert",
                        fontSize = 11.sp,
                        color = StudioColors.TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Why it was muted (AI Explanation Card)
            StudioCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "✨", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (!isUrgent) "Why it was muted" else "Why it was shown",
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
                                text = "🚗 $activeMode · Active",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = StudioColors.ActiveVioletLight,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (!isUrgent)
                            "This looks like a casual conversation or social invitation, not an urgent time-sensitive request. $activeMode quiets non-essential chat so directions and emergency calls stay in focus."
                        else
                            "This notification contains time-sensitive navigation, security verification, or VIP contact details matching your active $activeMode rules.",
                        fontSize = 13.sp,
                        color = StudioColors.TextSecondary,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Navigation prompts and priority contacts can still get through.",
                        fontSize = 11.sp,
                        color = StudioColors.TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Action Buttons
            Button(
                onClick = {
                    isUrgent = !isUrgent
                    CoroutineScope(Dispatchers.IO).launch {
                        val dao = database.notificationDao()
                        if (notifId != -1) {
                            val notif = dao.getLearnedRules().find { it.id == notifId }
                            if (notif != null) {
                                dao.insert(notif.copy(isUrgent = isUrgent, isLearned = learnFromChoice))
                            }
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, if (isUrgent) "Status updated: Shown" else "Status updated: Muted", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = StudioColors.ActiveViolet)
            ) {
                Text(
                    text = if (!isUrgent) "🔔 Show notification" else "🔕 Keep muted",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        isUrgent = false
                        Toast.makeText(context, "Notification kept muted", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, StudioColors.StudioBorder)
                ) {
                    Text("🔕 Keep muted", color = Color.White, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        try {
                            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                            if (intent != null) context.startActivity(intent)
                            else Toast.makeText(context, "App not installed", Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {
                            Toast.makeText(context, "Cannot open app", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, StudioColors.StudioBorder)
                ) {
                    Text("↗ Open app", color = Color.White, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Learn from this choice Card
            StudioCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Learn from this choice",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Your feedback stays on this device.",
                                fontSize = 11.sp,
                                color = StudioColors.TextMuted
                            )
                        }
                        Switch(
                            checked = learnFromChoice,
                            onCheckedChange = { learnFromChoice = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = StudioColors.ActiveViolet
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Was this helpful?",
                            fontSize = 13.sp,
                            color = StudioColors.TextSecondary
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                onClick = {
                                    feedbackSubmitted = true
                                    Toast.makeText(context, "Feedback recorded: Helpful", Toast.LENGTH_SHORT).show()
                                },
                                shape = CircleShape,
                                color = if (feedbackSubmitted == true) StudioColors.ActiveViolet else StudioColors.CardSurfaceHighlight,
                                border = BorderStroke(1.dp, StudioColors.StudioBorder)
                            ) {
                                Text(
                                    text = "Yes",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }

                            Surface(
                                onClick = {
                                    feedbackSubmitted = false
                                    Toast.makeText(context, "Feedback recorded: Not quite", Toast.LENGTH_SHORT).show()
                                },
                                shape = CircleShape,
                                color = if (feedbackSubmitted == false) StudioColors.RedSurface else StudioColors.CardSurfaceHighlight,
                                border = BorderStroke(1.dp, StudioColors.StudioBorder)
                            ) {
                                Text(
                                    text = "Not quite",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (feedbackSubmitted == false) StudioColors.RedText else Color.White,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Footer
            StudioFooter(text = "Your notifications stay on this device.")
        }
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 800)
@Composable
fun NotificationDetailPreview() {
    FocusRefineTheme {
        NotificationDetailScreen(
            notifId = 1,
            title = "WhatsApp",
            text = "Hey! Want to grab coffee before the meeting?",
            packageName = "com.whatsapp",
            timestamp = System.currentTimeMillis(),
            initialUrgent = false,
            onBack = {}
        )
    }
}

