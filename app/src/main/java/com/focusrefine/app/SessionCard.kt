package com.focusrefine.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Asked when Focus Refine is switched on: how long it should work before the usual mode comes back. [onStart] gets the
 * chosen length in minutes, or null for "until I turn it off". Cancel leaves Focus Refine off.
 */
@Composable
fun SessionDurationDialog(mode: String, onStart: (minutes: Int?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How long should Focus Refine work?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$mode stays on for the time you pick, then your usual mode comes back.", fontSize = 13.sp)
                for (minutes in ModeSession.DURATIONS_MIN) {
                    Surface(
                        onClick = { onStart(minutes) },
                        shape = CircleShape,
                        color = StudioColors.ActiveViolet,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "$minutes minutes",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 16.dp)
                        )
                    }
                }
                Surface(
                    onClick = { onStart(null) },
                    shape = CircleShape,
                    color = StudioColors.CardSurface,
                    border = BorderStroke(1.dp, StudioColors.ActiveViolet),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Until I turn it off",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 16.dp)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * The card shown while a session runs: mode, time left, muted count, a progress bar and End session. [now] is the
 * current time in ms; Home refreshes it every 30 seconds so the card stays current without a broadcast.
 */
@Composable
fun SessionActiveCard(session: ModeSession.State, now: Long, onEnd: () -> Unit, modifier: Modifier = Modifier) {
    // Home's clock can be a little older than a session that has just started; never show more than the full length.
    val now = maxOf(now, session.startMs)
    StudioCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${session.mode} session", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        if (session.openEnded) "On until you turn it off · ${session.mutedSoFar} muted so far"
                        else "${ModeSession.minutesLeft(session.endMs, now)} min left · ${session.mutedSoFar} muted so far",
                        fontSize = 12.sp,
                        color = StudioColors.TextSecondary
                    )
                }
                TextButton(onClick = onEnd) { Text("End session", color = StudioColors.ActiveVioletLight) }
            }
            if (!session.openEnded) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { ModeSession.percent(session.startMs, session.endMs, now) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = StudioColors.ActiveViolet,
                    trackColor = StudioColors.StudioBorder
                )
            }
        }
    }
}
