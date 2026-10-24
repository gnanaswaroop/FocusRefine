package com.focusrefine.app

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class NotificationDetailActivity : FragmentActivity() {

    private var isUnlocked = mutableStateOf(false)
    private var showAuthError = mutableStateOf(false)

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
        val explanation = intent.getStringExtra("explanation")
        val confidence = intent.getFloatExtra("confidence", -1f).takeIf { it >= 0f }

        setContent {
            FocusRefineTheme {
                NotificationDetailScreen(
                    notifId = notifId,
                    title = title,
                    text = text,
                    packageName = packageName,
                    timestamp = timestamp,
                    initialUrgent = initialUrgent,
                    explanation = explanation,
                    confidence = confidence,
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
    explanation: String?,
    onBack: () -> Unit,
    confidence: Float? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)
    val database = AppDatabase.getDatabase(context)
    val defaultMode = remember { PromptManager.getDefaultModeName(context) }
    val activeMode = remember { prefs.getString("current_mode", defaultMode) ?: defaultMode }

    var isUrgent by remember { mutableStateOf(initialUrgent) }
    var learnFromChoice by remember { mutableStateOf(true) }
    var decidedByUser by remember { mutableStateOf(false) }
    var feedbackSubmitted by remember { mutableStateOf<Boolean?>(null) }

    val appLabel = remember(packageName) {
        val segments = packageName.split(".").filter { it.length > 2 && it !in setOf("com", "org", "net", "android", "apps", "app") }
        val rawName = segments.lastOrNull() ?: packageName.substringAfterLast(".")
        rawName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    val displayExplanation = explanation.takeIf { !it.isNullOrBlank() }
        ?: (if (!isUrgent)
            "This looks like a casual conversation or social invitation, not an urgent time-sensitive request. $activeMode quiets non-essential chat so directions and emergency calls stay in focus."
        else
            "This notification contains time-sensitive navigation, security verification, or VIP contact details matching your active $activeMode rules.")

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
                            text = if (!isUrgent) "Muted: Routine Communication" else "High Urgency: Priority Prompt",
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

                    // How sure was the app? Only shown while the app's own decision stands (not after the user's choice).
                    if (!decidedByUser && confidence != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "How sure: ", fontSize = 11.sp, color = StudioColors.TextMuted)
                            Text(
                                text = Confidence.label(confidence) ?: "",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (Confidence.isUncertain(confidence)) StudioColors.GoldText else StudioColors.TextSecondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            ConfidenceMeter(confidence)
                        }
                    } else if (decidedByUser) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "You decided this one.", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StudioColors.GreenText)
                    }
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
                                text = "$activeMode · Active",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = StudioColors.ActiveVioletLight,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = displayExplanation,
                        fontSize = 13.sp,
                        color = StudioColors.TextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!decidedByUser && Confidence.isUncertain(confidence)) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = StudioColors.GoldSurface.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, StudioColors.GoldAccent.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Close call", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = StudioColors.GoldText)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "The app wasn't sure whether this should interrupt you. Telling it what you want takes one tap, " +
                                "stays on this phone, and helps it get similar ones right.",
                            fontSize = 12.sp,
                            color = StudioColors.TextSecondary,
                            lineHeight = 17.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 3. Action Buttons
            Button(
                onClick = {
                    isUrgent = !isUrgent
                    decidedByUser = true
                    CoroutineScope(Dispatchers.IO).launch {
                        val dao = database.notificationDao()
                        if (notifId != -1) {
                            val notif = dao.getLearnedRules().find { it.id == notifId } ?: dao.getAll().find { it.id == notifId }
                            if (notif != null) FeedbackRecorder.apply(context, notif, isUrgent, activeMode, learn = learnFromChoice)
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, if (isUrgent) "Preference saved: Show in future" else "Preference saved: Hide in future", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = StudioColors.ActiveViolet, contentColor = Color.White)
            ) {
                Text(
                    text = if (!isUrgent) "🔔 Show notifications like these in the future" else "🔕 Hide notifications like these in the future",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        Toast.makeText(context, "Preference kept", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, StudioColors.StudioBorder)
                ) {
                    Text(
                        text = if (!isUrgent) "🔕 Keep hiding these" else "🔔 Keep showing these",
                        color = Color.White,
                        fontSize = 12.sp
                    )
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
            explanation = "Did not contain urgent keywords or high AI confidence for Deep Work mode.",
            onBack = {}
        )
    }
}
