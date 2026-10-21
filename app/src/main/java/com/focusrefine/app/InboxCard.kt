package com.focusrefine.app

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Three small bars: one filled = not sure, two = fairly sure, three = very sure. Shown only when a confidence is known. */
@Composable
fun ConfidenceMeter(confidence: Float?, modifier: Modifier = Modifier) {
    if (confidence == null) return
    val filled = when {
        confidence >= Confidence.VERY_SURE_FROM -> 3
        confidence >= Confidence.UNCERTAIN_BELOW -> 2
        else -> 1
    }
    val color = if (Confidence.isUncertain(confidence)) StudioColors.GoldAccent else StudioColors.GreenAccent
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..3) {
            Box(
                modifier = Modifier
                    .size(width = 10.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i <= filled) color else StudioColors.StudioBorder)
            )
        }
    }
}

/** Amber "Not sure" pill; empty for confident decisions. */
@Composable
fun NotSurePill(confidence: Float?) {
    if (!Confidence.isUncertain(confidence)) return
    Surface(
        shape = CircleShape,
        color = StudioColors.GoldSurface,
        border = BorderStroke(1.dp, StudioColors.GoldAccent.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Not sure", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = StudioColors.GoldText)
            Spacer(modifier = Modifier.width(6.dp))
            ConfidenceMeter(confidence)
        }
    }
}

/** Starts the detail screen for [notif]. */
fun openNotificationDetails(context: Context, notif: NotificationEntity) {
    context.startActivity(Intent(context, NotificationDetailActivity::class.java).apply {
        putExtra("notif_id", notif.id)
        putExtra("title", notif.title)
        putExtra("text", notif.text)
        putExtra("package_name", notif.packageName)
        putExtra("timestamp", notif.timestamp)
        putExtra("is_urgent", notif.isUrgent)
        putExtra("explanation", notif.explanation)
        putExtra("confidence", notif.confidence ?: -1f)
    })
}

/**
 * One inbox row. Decisions the app was not sure about get an amber outline, a "Not sure" pill and two one-tap
 * buttons so the user can settle them without opening the details; every choice goes through [FeedbackRecorder].
 * [onChanged] is called after a choice has been stored so the list can refresh.
 */
@Composable
fun InboxNotificationCard(notif: NotificationEntity, onChanged: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    val context = LocalContext.current
    val isMuted = !notif.isUrgent
    val uncertain = notif.isUncertain()

    fun choose(makeUrgent: Boolean) {
        val prefs = context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)
        val mode = prefs.getString("current_mode", "Default") ?: "Default"
        CoroutineScope(Dispatchers.IO).launch {
            FeedbackRecorder.apply(context, notif, makeUrgent, mode)
            onChanged()
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    when {
                        makeUrgent == notif.isUrgent -> "Got it: kept ${if (makeUrgent) "shown" else "muted"}"
                        makeUrgent -> "Moved to Shown"
                        else -> "Moved to Muted"
                    },
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    StudioCard(
        onClick = { openNotificationDetails(context, notif) },
        modifier = modifier,
        border = if (uncertain) BorderStroke(1.5.dp, StudioColors.GoldAccent.copy(alpha = 0.6f)) else BorderStroke(1.dp, StudioColors.StudioBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    AppIconImage(packageName = notif.packageName, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = notif.title ?: "Notification",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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

            if (uncertain) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = StudioColors.GoldSurface.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, StudioColors.GoldAccent.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = if (isMuted) "Close call: the app muted this but could be wrong."
                            else "Close call: the app showed this but could be wrong.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StudioColors.GoldText
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { choose(!notif.isUrgent) },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = StudioColors.ActiveViolet, contentColor = Color.White),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isMuted) "Show it" else "Mute it", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { choose(notif.isUrgent) },
                                shape = CircleShape,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isMuted) "Keep muted" else "Keep shown", fontSize = 12.sp, color = Color.White)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Your choice stays on this phone and teaches the app.",
                            fontSize = 10.sp,
                            color = StudioColors.TextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    NotSurePill(if (uncertain) notif.confidence else null)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { choose(!notif.isUrgent) }, modifier = Modifier.size(28.dp)) {
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

/** Banner above the inbox: how many decisions need a look, and a switch between the review list and the normal tabs. */
@Composable
fun ReviewBanner(count: Int, reviewing: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    if (count == 0 && !reviewing) return
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = StudioColors.GoldSurface.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, StudioColors.GoldAccent.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (reviewing) "Reviewing close calls" else "$count close ${if (count == 1) "call" else "calls"} to review",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = StudioColors.GoldText
                )
                Text(
                    text = if (reviewing) "Least sure first. Your choices teach the app and stay on this phone."
                    else "The app wasn't sure about these. A quick tap makes it smarter.",
                    fontSize = 11.sp,
                    color = StudioColors.TextSecondary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onToggle,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = StudioColors.GoldAccent, contentColor = Color.Black)
            ) {
                Text(if (reviewing) "Done" else "Review", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * The quick filter for close calls, shown with the Muted and Shown tabs whenever there is something to review. One tap
 * lists only the decisions the app was not sure about (least sure first, across both tabs); tap again, or pick a tab, to
 * go back. [count] is the number waiting.
 */
@Composable
fun CloseCallsChip(count: Int, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (active) StudioColors.GoldAccent else StudioColors.GoldSurface.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, StudioColors.GoldAccent.copy(alpha = if (active) 1f else 0.5f)),
        modifier = modifier.semantics {
            role = Role.Switch
            stateDescription = if (active) "On" else "Off"
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "\u26A0 Close calls ($count)" + if (active) "  \u2715" else "",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) Color.Black else StudioColors.GoldText,
                maxLines = 1
            )
        }
    }
}
